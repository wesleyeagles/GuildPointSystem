package com.guild.app.pbhistory.service;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.exception.AppException;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.websocket.WebSocketPublisher;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PbHistoryTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean WebSocketPublisher ws;
    @Autowired PbHistoryService service;
    @Autowired MemberRepository members;
    long aId, bId, cId, adminId;
    String aNick, bNick, cNick;
    Instant t0 = Instant.parse("2026-11-05T17:00:00Z");
    long serial = 800_000_000L + (long) (Math.random() * 1_000_000);

    long member(String nick, String status, String role) {
        jdbc.update("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,?,?,true)", nick, role, status);
        return jdbc.queryForObject("SELECT id FROM member WHERE nickname=?", Long.class, nick);
    }
    static String nick(String prefix) { return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8); }
    MemberPrincipal as(long id) { return new MemberPrincipal(members.findById(id).orElseThrow()); }
    void at(int seconds) { service.useClock(Clock.fixed(t0.plusSeconds(seconds), ZoneOffset.UTC)); }

    ResultActions send(String character, int map, String body) throws Exception {
        var request = post("/api/pb-history/game").contentType("text/plain;charset=UTF-8").param("map", String.valueOf(map));
        if (character != null) request.param("character", character);
        return mvc.perform(request.content(body.getBytes(StandardCharsets.UTF_8)));
    }
    String ok(String character, String body) throws Exception {
        return send(character, 1, body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
    String alive(int hp) { return "M\tCora HQ\nP\t" + serial + "\t100\t200\t" + hp + "\tWarbeast\n"; }
    String dead() { return "D\t" + serial + "\tWarbeast\n"; }

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM pb_kill");
        jdbc.update("DELETE FROM pb_boss");
        jdbc.update("DELETE FROM pb_candidate");
        aNick = nick("Aaa"); aId = member(aNick, "APROVADO", "MEMBRO");
        bNick = nick("Bbb"); bId = member(bNick, "APROVADO", "MEMBRO");
        cNick = nick("Ccc"); cId = member(cNick, "APROVADO", "MEMBRO");
        adminId = member(nick("Adm"), "APROVADO", "ADMINISTRADOR");
        at(0);
        service.saveBoss("[65]Warbeast", "Warbeast (QG)", "ROTACAO", as(adminId));   // nível colado: tira
    }
    @AfterEach void reset() { service.useClock(Clock.systemUTC()); }

    @Test void presentMeansSawAliveAndWasNearDeath() throws Exception {
        assertEquals("A\t1\nB\tWarbeast\n", ok(aNick, alive(100)));
        ok(bNick, alive(90));
        ok(cNick, alive(80));
        at(60);
        ok(aNick, alive(40));
        ok(bNick, alive(35));                       // B não manda a morte, mas foi visto 5 s antes: presente
        at(65);
        ok(aNick, dead());
        ok(cNick, dead());                           // C viu vivo e avisou a morte: presente
        String late = nick("Lat");
        member(late, "APROVADO", "MEMBRO");
        ok(late, dead());                            // só viu o corpo: não conta

        var kills = service.kills(null, null, null, 10, as(adminId));
        assertEquals(1, kills.size());
        var kill = kills.get(0);
        assertEquals("Warbeast", kill.boss());
        assertEquals("Warbeast (QG)", kill.label());
        assertEquals("Cora HQ", kill.mapName());
        assertEquals(t0.plusSeconds(65), kill.diedAt());
        assertEquals(35, kill.minHp());
        assertEquals(3, kill.members().size());
        assertTrue(kill.members().stream().allMatch(PbHistoryService.Presence::present));

        var attendance = service.attendance(30, as(adminId));
        assertEquals(1, attendance.kills());
        assertEquals(3, attendance.rows().size());
        assertTrue(attendance.rows().stream().allMatch(r -> r.present() == 1 && r.seen() == 1));
    }

    @Test void leftBeforeTheDeathIsOnlySeen() throws Exception {
        ok(aNick, alive(100));
        ok(bNick, alive(100));
        at(120);
        ok(aNick, alive(10));
        at(125);
        ok(aNick, dead());
        var members = service.kills(null, null, null, 10, as(adminId)).get(0).members();
        assertTrue(members.stream().filter(p -> p.memberId() == aId).findFirst().orElseThrow().present());
        assertFalse(members.stream().filter(p -> p.memberId() == bId).findFirst().orElseThrow().present());
        var b = service.attendance(30, as(adminId)).rows().stream().filter(r -> r.memberId() == bId).findFirst().orElseThrow();
        assertEquals(0, b.present());
        assertEquals(1, b.seen());
    }

    @Test void untrackedNamesAreIgnoredAndCandidatesSurface() throws Exception {
        ok(aNick, "P\t" + serial + "\t1\t2\t50\tTurncoat Hunter\nC\tSplinter\nC\tWarbeast\n");
        ok(bNick, "C\tSplinter\nC\t[43]Bad\n");
        assertEquals(0, service.kills(null, null, null, 10, as(adminId)).size());
        var overview = service.overview(as(adminId));
        assertEquals(1, overview.candidates().size());               // Warbeast já é boss; nome com nível descartado
        assertEquals("Splinter", overview.candidates().get(0).name());
        assertEquals(2, overview.candidates().get(0).seenCount());

        service.saveBoss("Splinter", null, "EXTRA", as(adminId));
        assertTrue(service.overview(as(adminId)).candidates().isEmpty());
        assertTrue(ok(aNick, "").contains("B\tSplinter\n"));
        service.removeBoss("Splinter", as(adminId));
        assertFalse(ok(aNick, "").contains("Splinter"));
    }

    @Test void newSpawnAfterSixHoursIsAnotherKill() throws Exception {
        ok(aNick, alive(100));
        ok(aNick, dead());
        ok(aNick, alive(100));                       // aviso atrasado depois da morte: não reabre
        assertEquals(1, service.kills(null, null, null, 10, as(adminId)).size());
        at(7 * 3600);
        ok(aNick, alive(100));
        assertEquals(2, service.kills(null, null, null, 10, as(adminId)).size());
        assertEquals(1, service.kills(null, aId, service.kills(null, null, null, 10, as(adminId)).get(0).id(), 10, as(adminId)).size());
    }

    @Test void adminOnlyAndApprovedSenders() throws Exception {
        String pending = nick("Pen");
        member(pending, "PENDENTE", "MEMBRO");
        send(pending, 1, alive(100)).andExpect(status().isForbidden());
        send(null, 1, "").andExpect(status().isForbidden());
        assertFalse(service.overview(as(aId)).canView());
        assertThrows(AppException.class, () -> service.kills(null, null, null, 10, as(aId)));
        assertThrows(AppException.class, () -> service.attendance(30, as(aId)));
        assertThrows(AppException.class, () -> service.saveBoss("X", null, "EXTRA", as(aId)));
        assertThrows(AppException.class, () -> service.saveBoss("Ok Name", null, "OUTRO", as(adminId)));
    }
}
