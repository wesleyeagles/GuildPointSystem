package com.guild.app.roulette;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.exception.AppException;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.roulette.service.RouletteService;
import com.guild.app.roulette.service.RouletteService.NewRoulette;
import com.guild.app.roulette.service.RouletteService.Sent;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RouletteTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired RouletteService service;
    @Autowired MemberRepository members;
    @MockitoBean WebSocketPublisher ws;
    String leader, a, b;
    long leaderId, adminId, aId, bId;
    static final Instant NOW = Instant.parse("2026-11-17T15:00:00Z");

    long member(String nick, String role) {
        jdbc.update("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,?,'APROVADO',true)", nick, role);
        return jdbc.queryForObject("SELECT id FROM member WHERE nickname=?", Long.class, nick);
    }
    static String nick(String p) { return p + UUID.randomUUID().toString().replace("-", "").substring(0, 8); }
    MemberPrincipal as(long id) { return new MemberPrincipal(members.findById(id).orElseThrow()); }
    void at(Instant when) { service.useClock(Clock.fixed(when, ZoneOffset.UTC)); }

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM roulette");
        jdbc.update("UPDATE member SET role='ADMINISTRADOR' WHERE role='LIDER'");  // só pode haver um Líder
        leader = nick("Ldr");
        leaderId = member(leader, "LIDER");
        adminId = member(nick("Adm"), "ADMINISTRADOR");
        a = nick("Aaa");
        aId = member(a, "MEMBRO");
        b = nick("Bbb");
        bId = member(b, "MEMBRO");
        at(NOW);
    }

    MvcResult game(String character) throws Exception {
        return mvc.perform(get("/api/roulettes/game").param("character", character)).andReturn();
    }

    @Test void onlyAdminOrLeaderCreates() {
        assertFalse(service.view(as(aId)).canCreate());
        AppException e = assertThrows(AppException.class,
            () -> service.create(new NewRoulette("Arma +7", List.of(aId, bId), true), as(aId)));
        assertEquals(403, e.getStatus().value());
        assertTrue(service.view(as(adminId)).canCreate());
    }

    @Test void validatesPrizeAndEntries() {
        assertThrows(AppException.class, () -> service.create(new NewRoulette("  ", List.of(aId, bId), true), as(adminId)));
        assertThrows(AppException.class, () -> service.create(new NewRoulette("x".repeat(61), List.of(aId, bId), true), as(adminId)));
        // Repetido conta uma vez: só um membro.
        assertThrows(AppException.class, () -> service.create(new NewRoulette("Arma", List.of(aId, aId), true), as(adminId)));
        assertThrows(AppException.class, () -> service.create(new NewRoulette("Arma", List.of(aId, 999_999_999L), true), as(adminId)));
    }

    @Test void drawsWinnerAndHidesItWhileSpinning() {
        service.useDraw(new Random(1) { @Override public int nextInt(int bound) { return 1; } });
        Sent sent = service.create(new NewRoulette("  Arma\t+7\n PvP ", List.of(aId, bId), false), as(leaderId)).recent().get(0);
        assertEquals("Arma +7 PvP", sent.prize());
        assertEquals(List.of(a, b), sent.entries());
        assertTrue(sent.spinning());
        assertNull(sent.winner());
        at(NOW.plus(RouletteService.LEAD).plus(RouletteService.SPIN));
        Sent done = service.view(as(adminId)).recent().get(0);
        assertFalse(done.spinning());
        assertEquals(b, done.winner());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE type='ROULETTE_SPUN'", Integer.class));
    }

    @Test void oneAtATime() {
        service.create(new NewRoulette("Um", List.of(aId, bId), true), as(adminId));
        AppException e = assertThrows(AppException.class,
            () -> service.create(new NewRoulette("Dois", List.of(aId, bId), true), as(adminId)));
        assertEquals(409, e.getStatus().value());
        at(NOW.plus(RouletteService.SHOWN).plusSeconds(1));
        assertDoesNotThrow(() -> service.create(new NewRoulette("Dois", List.of(aId, bId), true), as(adminId)));
    }

    @Test void gameShowsOnlyToLeaderWhileTesting() throws Exception {
        service.useDraw(new Random(1) { @Override public int nextInt(int bound) { return 0; } });
        long id = service.create(new NewRoulette("Capa rara", List.of(aId, bId, leaderId), true), as(adminId)).recent().get(0).id();
        at(NOW.plusMillis(1500));
        MvcResult r = game(leader);
        assertEquals(200, r.getResponse().getStatus());
        assertEquals(id + "\t1500\t7000\t10000\t12000\t0\tCapa rara\n" + a + "\n" + b + "\n" + leader + "\n",
            r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(204, game(a).getResponse().getStatus());
        assertEquals(204, game("ninguem").getResponse().getStatus());
        at(NOW.plus(RouletteService.SHOWN).plusMillis(1));
        assertEquals(204, game(leader).getResponse().getStatus());
    }

    @Test void gameShowsToEveryoneWhenOpen() throws Exception {
        service.create(new NewRoulette("Todos", List.of(aId, bId), false), as(adminId));
        assertEquals(200, game(a).getResponse().getStatus());
        assertEquals(200, game(leader).getResponse().getStatus());
    }
}
