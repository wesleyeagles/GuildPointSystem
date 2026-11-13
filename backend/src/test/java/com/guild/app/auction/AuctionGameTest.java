package com.guild.app.auction;

import com.guild.app.websocket.WebSocketPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuctionGameTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.guild.app.auction.service.AuctionGameService service;
    @MockitoBean WebSocketPublisher ws;

    String memberNick, pendingNick;
    long memberId, itemId;

    static String nick(String prefix) { return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8); }

    long member(String nick, String status) {
        return jdbc.queryForObject("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,'MEMBRO',?,true) RETURNING id",
            Long.class, nick, status);
    }

    long auction(String status, long bid, int quantity) {
        long id = jdbc.queryForObject("INSERT INTO auction(duration_minutes,status,created_by_id,starts_at,ends_at,current_bid) "
            + "VALUES(60,?,?,NOW(),NOW() + INTERVAL '1 hour',?) RETURNING id", Long.class, status, memberId, bid);
        jdbc.update("INSERT INTO auction_item(auction_id,item_id,quantity) VALUES(?,?,?)", id, itemId, quantity);
        return id;
    }

    @BeforeEach void setup() {
        // Leiloes de outros testes no mesmo banco ficam de fora: so os deste teste estao abertos.
        jdbc.update("UPDATE auction SET status='CLOSED' WHERE status IN ('OPEN','DOLE','TIE_BREAK')");
        Object target = org.springframework.test.util.AopTestUtils.getTargetObject(service);
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(target, "forget");
        memberNick = nick("Ana");
        pendingNick = nick("Pen");
        memberId = member(memberNick, "APROVADO");
        member(pendingNick, "PENDENTE");
        itemId = jdbc.queryForObject("INSERT INTO item(type,name,image_url,created_by_id) VALUES('MISC',?,'x.png',?) RETURNING id",
            Long.class, "Espada\tde Teste", memberId);
    }

    @Test void listsOpenAuctionsForApprovedCharacterOnly() throws Exception {
        long older = auction("OPEN", 0, 1);
        long newer = auction("DOLE", 250, 3);
        auction("PAUSED", 0, 1);
        auction("SCHEDULED", 0, 1);
        long end = jdbc.queryForObject("SELECT FLOOR(EXTRACT(EPOCH FROM ends_at))::bigint FROM auction WHERE id=?", Long.class, newer);
        long endOlder = jdbc.queryForObject("SELECT FLOOR(EXTRACT(EPOCH FROM ends_at))::bigint FROM auction WHERE id=?", Long.class, older);
        // Mesmo created_at (NOW() da transacao): a ordem entre os dois nao e garantida.
        mvc.perform(get("/api/auctions/game").param("character", memberNick.toUpperCase()))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.containsString(newer + "\t" + end + "\t250\tEspada de Teste x3\n"),
                org.hamcrest.Matchers.containsString(older + "\t" + endOlder + "\t0\tEspada de Teste\n"),
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PAUSED")))))
            .andExpect(result -> {
                String body = result.getResponse().getContentAsString();
                if (body.split("\n").length != 2) throw new AssertionError("esperava 2 linhas: " + body);
            });
        mvc.perform(get("/api/auctions/game").param("character", pendingNick)).andExpect(status().isNoContent());
        mvc.perform(get("/api/auctions/game").param("character", "ninguem")).andExpect(status().isNoContent());
        mvc.perform(get("/api/auctions/game")).andExpect(status().isNoContent());
    }

    @Test void noOpenAuctionIsNoContent() throws Exception {
        auction("SCHEDULED", 0, 1);
        mvc.perform(get("/api/auctions/game").param("character", memberNick)).andExpect(status().isNoContent());
    }
}
