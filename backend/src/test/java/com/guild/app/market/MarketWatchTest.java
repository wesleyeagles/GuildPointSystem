package com.guild.app.market;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.exception.AppException;
import com.guild.app.market.service.MarketPage.Listing;
import com.guild.app.market.service.MarketSource;
import com.guild.app.market.service.MarketWatchService;
import com.guild.app.market.service.MarketWatchService.NewWatch;
import com.guild.app.member.repository.MemberRepository;
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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MarketWatchTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired MarketWatchService service;
    @Autowired MemberRepository members;
    @Autowired com.guild.app.item.service.GameItemCard cards;
    @MockitoBean WebSocketPublisher ws;
    @MockitoBean MarketSource source;
    String coxinha, assassino;
    long coxinhaId, assassinoId, leaderId, memberId;

    long member(String nick, String role) {
        jdbc.update("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,?,'APROVADO',true)", nick, role);
        return jdbc.queryForObject("SELECT id FROM member WHERE nickname=?", Long.class, nick);
    }
    static String nick(String p) { return p + UUID.randomUUID().toString().replace("-", "").substring(0, 8); }
    MemberPrincipal as(long id) { return new MemberPrincipal(members.findById(id).orElseThrow()); }
    static Listing l(String code, int up, int qty, long price, long id) { return new Listing(code, "Sword " + code, up, qty, price, id); }

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM market_watch");
        coxinha = nick("Cox");
        coxinhaId = member(coxinha, "MEMBRO");
        assassino = nick("Ass");
        assassinoId = member(assassino, "MEMBRO");
        memberId = member(nick("Mem"), "MEMBRO");
        leaderId = jdbc.queryForObject("SELECT id FROM member WHERE role='LIDER' ORDER BY id LIMIT 1", Long.class);
    }

    String alerts(String character) throws Exception {
        return mvc.perform(get("/api/market/game/alerts").param("character", character))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test void onlyAdminOrLeaderEdits() {
        assertFalse(service.view(as(memberId)).canConfigure());
        assertThrows(AppException.class, () -> service.add(new NewWatch(coxinhaId, "iwshi28", "X", null, null), as(memberId)));
        assertThrows(AppException.class, () -> service.add(new NewWatch(coxinhaId, "../x", "X", null, null), as(leaderId)));
        long admin = member(nick("Adm"), "ADMINISTRADOR");
        assertEquals(1, service.add(new NewWatch(coxinhaId, "IWSHI28", "Vampire", null, null), as(admin)).watches().size());
        assertThrows(AppException.class, () -> service.add(new NewWatch(coxinhaId, "iwshi28", "Vampire", null, null), as(leaderId)));
    }

    @Test void eachMemberGetsOnlyTheirItemAndOnlyOncePerListing() throws Exception {
        service.add(new NewWatch(coxinhaId, "iwshi28", "Vampire", null, null), as(leaderId));
        service.add(new NewWatch(assassinoId, "iwaaa01", "Outro", 3, 500L), as(leaderId));
        when(source.listings("iwshi28")).thenReturn(Optional.of(List.of(l("iwshi28", 0, 1, 1499, 10))));
        when(source.listings("iwaaa01")).thenReturn(Optional.of(List.of(
            l("iwaaa01", 2, 1, 100, 20),      // upgrade baixo
            l("iwaaa01", 4, 1, 900, 21),      // caro
            l("iwaaa01", 4, 2, 800, 22))));   // 400 por unidade: serve
        assertEquals(2, service.scan());
        String cox = alerts(coxinha);
        assertTrue(cox.matches("\\d+\t\\d{10}\t1499\tiwshi28\tSword iwshi28\n"), cox);
        String ass = alerts(assassino);
        assertTrue(ass.matches("\\d+\t\\d{10}\t800\tiwaaa01\tSword iwaaa01 \\+4 x2\n"), ass);

        assertEquals(0, service.scan());                          // mesmo anúncio: não repete
        when(source.listings("iwshi28")).thenReturn(Optional.of(List.of()));
        assertEquals(0, service.scan());                          // vendido
        when(source.listings("iwshi28")).thenReturn(Optional.of(List.of(l("iwshi28", 0, 1, 1499, 10))));
        assertEquals(1, service.scan());                          // voltou: avisa de novo
    }

    @Test void marketDownKeepsWhatWasSeen() {
        service.add(new NewWatch(coxinhaId, "iwshi28", "Vampire", null, null), as(leaderId));
        when(source.listings("iwshi28")).thenReturn(Optional.of(List.of(l("iwshi28", 0, 1, 1499, 10))));
        assertEquals(1, service.scan());
        when(source.listings("iwshi28")).thenReturn(Optional.empty());
        assertEquals(0, service.scan());
        when(source.listings("iwshi28")).thenReturn(Optional.of(List.of(l("iwshi28", 0, 1, 1499, 10))));
        assertEquals(0, service.scan());
    }

    @Test void capsAlertsPerWatchCheapestFirst() {
        service.add(new NewWatch(coxinhaId, "iwshi28", "Vampire", null, null), as(leaderId));
        when(source.listings("iwshi28")).thenReturn(Optional.of(List.of(
            l("iwshi28", 0, 1, 50, 1), l("iwshi28", 0, 1, 10, 2), l("iwshi28", 0, 1, 40, 3),
            l("iwshi28", 0, 1, 20, 4), l("iwshi28", 0, 1, 30, 5))));
        assertEquals(MarketWatchService.ALERTS_PER_WATCH, service.scan());
        assertEquals(List.of(10L, 20L, 30L), jdbc.queryForList("SELECT price FROM market_alert WHERE member_id=? ORDER BY price", Long.class, coxinhaId));
        assertEquals(0, service.scan());
    }

    @Test void relicFromCatalogUsesTheMarketCode() throws Exception {
        service.add(new NewWatch(coxinhaId, "relic020", "(2GR) Brutal Rex Relic", null, null), as(leaderId));
        when(source.listings("iyfip06")).thenReturn(Optional.of(List.of(new Listing("iyfip06", "(2GR) Brutal Rex Relic", 0, 1, 8500, 7))));
        assertEquals(1, service.scan());
        String cox = alerts(coxinha);
        assertTrue(cox.matches("\\d+\t\\d{10}\t8500\tiyfip06\t\\(2GR\\) Brutal Rex Relic\n"), cox);
    }

    @Test void relicEffectsAndIconForTheGameCard() throws Exception {
        assertEquals("Force attack +3.5%", cards.effects("relic020"));
        assertEquals("Force attack +3.5%", cards.effects("iyfip06"));   // código do market
        assertEquals("", cards.effects("iwshi28"));
        assertTrue(cards.iconKey("iyfip06").startsWith("/relics/item-1-6.jpg|951|32"), cards.iconKey("iyfip06"));
        assertTrue(cards.icon("../etc/passwd", 1, 32).isEmpty());
        service.add(new NewWatch(coxinhaId, "relic020", "(2GR) Brutal Rex Relic", null, null), as(leaderId));
        when(source.listings("iyfip06")).thenReturn(Optional.of(List.of(new Listing("iyfip06", "(2GR) Brutal Rex Relic", 0, 1, 8500, 7))));
        service.scan();
        String body = mvc.perform(get("/api/market/game/alerts").param("character", coxinha).param("details", "1"))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.endsWith("\tForce attack +3.5%\t/relics/item-1-6.jpg|951|32\n"), body);
    }

    @Test void nothingForStrangers() throws Exception {
        mvc.perform(get("/api/market/game/alerts").param("character", "NinguemAqui")).andExpect(status().isNoContent());
        mvc.perform(get("/api/market/game/alerts").param("character", coxinha)).andExpect(status().isNoContent());
    }
}
