package com.guild.app.auction;

import com.guild.app.websocket.WebSocketPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Leilão pelo jogo: lance superado, lista, detalhe e lance; e o aviso de superado no site. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuctionGameBidTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean WebSocketPublisher ws;
    @Autowired com.guild.app.auction.service.AuctionService auctions;

    final LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    String ana, bia;
    long anaId, biaId, auctionId;

    static String nick(String prefix) { return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8); }

    long member(String nick, long points) {
        return jdbc.queryForObject("INSERT INTO member(nickname,role,status,profile_complete,points,blocked_for_sell_until) "
            + "VALUES(?,'MEMBRO','APROVADO',true,?,?) RETURNING id", Long.class, nick, points, today);
    }

    @BeforeEach void setup() {
        jdbc.update("UPDATE auction SET status='CLOSED' WHERE status IN ('OPEN','DOLE','TIE_BREAK','PAUSED')");
        ana = nick("Ana");
        bia = nick("Bia");
        anaId = member(ana, 1000);
        biaId = member(bia, 1000);
        long item = jdbc.queryForObject("INSERT INTO item(type,name,image_url,created_by_id) VALUES('MISC','Espada','x.png',?) RETURNING id",
            Long.class, anaId);
        auctionId = jdbc.queryForObject("INSERT INTO auction(duration_minutes,status,created_by_id,starts_at,ends_at,current_bid) "
            + "VALUES(60,'OPEN',?,NOW(),NOW() + INTERVAL '1 hour',0) RETURNING id", Long.class, anaId);
        jdbc.update("INSERT INTO auction_item(auction_id,item_id,quantity) VALUES(?,?,1)", auctionId, item);
    }

    void bid(String character, String amount, int expected) throws Exception {
        mvc.perform(post("/api/auctions/game/" + auctionId + "/bid").param("character", character)
                .contentType(MediaType.TEXT_PLAIN).content(amount))
            .andExpect(status().is(expected));
    }

    @Test void bidFromTheGameFollowsTheSiteRules() throws Exception {
        bid(ana, "50", 200);
        mvc.perform(post("/api/auctions/game/" + auctionId + "/bid").param("character", bia)
                .contentType(MediaType.TEXT_PLAIN).content("55"))
            .andExpect(status().isConflict())
            .andExpect(content().string(containsString("lance mínimo é 60")));
        bid(bia, "abc", 400);
        bid("ninguem", "60", 403);
        mvc.perform(post("/api/auctions/game/" + auctionId + "/bid").param("character", bia)
                .contentType(MediaType.TEXT_PLAIN).content("60"))
            .andExpect(status().isOk())
            .andExpect(content().string("60\t60\n"));
    }

    @Test void outbidListsOnlyWhoWasPassed() throws Exception {
        bid(ana, "50", 200);
        mvc.perform(get("/api/auctions/game/outbid").param("character", ana)).andExpect(status().isNoContent());
        bid(bia, "60", 200);
        mvc.perform(get("/api/auctions/game/outbid").param("character", ana))
            .andExpect(status().isOk())
            .andExpect(content().string(matchesPattern("[0-9]+\t[0-9]+\t60\t" + auctionId + "\t70\t1000\tEspada\t\t\n")));
        mvc.perform(get("/api/auctions/game/outbid").param("character", bia)).andExpect(status().isNoContent());
    }

    @Test void outbidMemberIsNotifiedOnTheSite() throws Exception {
        bid(ana, "50", 200);
        bid(bia, "60", 200);
        verify(ws).publishAuctionOutbid(eq(anaId), argThat(p -> p instanceof Map<?, ?> m
            && Long.valueOf(auctionId).equals(m.get("auctionId")) && Long.valueOf(60).equals(m.get("amount"))
            && bia.equals(m.get("bidder"))));
        verify(ws, never()).publishAuctionOutbid(eq(biaId), any());
    }

    @Test void listAndDetailShowMyStateAndSystemMessages() throws Exception {
        bid(ana, "50", 200);
        bid(bia, "60", 200);
        mvc.perform(get("/api/auctions/game/list").param("character", ana))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.startsWith("B\t1000\t")))
            .andExpect(content().string(containsString("A\t" + auctionId + "\tOPEN\t")))
            .andExpect(content().string(containsString("\t60\t70\t1\t" + bia + "\tEspada\t")));
        mvc.perform(get("/api/auctions/game/list").param("character", bia))
            .andExpect(content().string(org.hamcrest.Matchers.startsWith("B\t940\t")))
            .andExpect(content().string(containsString("\t60\t70\t2\t" + bia + "\t")));
        mvc.perform(get("/api/auctions/game/" + auctionId).param("character", ana))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("M\t")))
            .andExpect(content().string(containsString("BID\t" + bia + " deu um lance de 60 pts\n")))
            .andExpect(content().string(matchesPattern("(?s).*\nL\t[0-9]+\t60\t0\t" + bia + "\nL\t[0-9]+\t50\t0\t" + ana + "\n$")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("TEXT\t"))));
        mvc.perform(get("/api/auctions/game/list").param("character", "ninguem")).andExpect(status().isForbidden());
    }

    @Test void participatingIdsForTheSiteTabs() throws Exception {
        bid(ana, "50", 200);
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(auctionId), auctions.participatingIds(anaId));
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(), auctions.participatingIds(biaId));
    }

    @Test void siteItemWithUploadedImageGetsAGameIcon() throws Exception {
        // Imagem enviada no site (não é sprite do jogo): 64x40 vermelha, vira 32x32 centralizada.
        String file = UUID.randomUUID() + ".png";
        java.nio.file.Path dir = java.nio.file.Path.of("./target/test-uploads");
        java.nio.file.Files.createDirectories(dir);
        var image = new java.awt.image.BufferedImage(64, 40, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(java.awt.Color.RED);
        g.fillRect(0, 0, 64, 40);
        g.dispose();
        javax.imageio.ImageIO.write(image, "png", dir.resolve(file).toFile());
        jdbc.update("UPDATE item SET image_url=? WHERE id IN (SELECT item_id FROM auction_item WHERE auction_id=?)",
            "/uploads/" + file, auctionId);
        String key = "/uploads/" + file + "|0|1";
        mvc.perform(get("/api/auctions/game/list").param("character", ana))
            .andExpect(content().string(containsString("\tEspada\t\t" + key + "\n")));
        byte[] icon = mvc.perform(get("/api/game/icon").param("sheet", "/uploads/" + file).param("icon", "0").param("cols", "1"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        org.junit.jupiter.api.Assertions.assertEquals(4096, icon.length);
        int middle = (16 * 32 + 16) * 4, top = (2 * 32 + 16) * 4;  // centro vermelho; faixa de cima preta (40 -> 20 px)
        org.junit.jupiter.api.Assertions.assertEquals((byte) 0xFF, icon[middle + 2]);
        org.junit.jupiter.api.Assertions.assertEquals(0, icon[top + 2]);
        // Fora da pasta de uploads ou outro formato: nada.
        mvc.perform(get("/api/game/icon").param("sheet", "/uploads/../x.png").param("icon", "0").param("cols", "1"))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/game/icon").param("sheet", "/uploads/" + file).param("icon", "1").param("cols", "1"))
            .andExpect(status().isNotFound());
    }
}
