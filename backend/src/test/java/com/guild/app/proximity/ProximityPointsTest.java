package com.guild.app.proximity;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.exception.AppException;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.proximity.service.ProximityPointsService;
import com.guild.app.proximity.service.ProximityPointsService.CreateRequest;
import com.guild.app.websocket.WebSocketPublisher;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Pontos por aproximação: pedido da staff, resposta da DLL da referência e pontos para quem estava perto. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProximityPointsTest {
    @Autowired ProximityPointsService service;
    @Autowired MemberRepository members;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @MockitoBean WebSocketPublisher ws;

    static Instant base = Instant.parse("2031-01-01T00:00:00Z");
    Instant now;
    String tag;
    long moderator, anchor, anchor2, near, far, pending;

    long member(String role, String status) {
        return jdbc.queryForObject("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,?,?,true) RETURNING id",
                Long.class, tag + role.charAt(0) + UUID.randomUUID().toString().substring(0, 4), role, status);
    }
    String nick(long id) { return members.findById(id).orElseThrow().getNickname(); }
    long points(long id) { em.flush(); return jdbc.queryForObject("SELECT points FROM member WHERE id=?", Long.class, id); }
    void loginAs(long id) {
        var p = new MemberPrincipal(members.findById(id).orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }
    void at(Instant instant) {
        Object target = AopTestUtils.getTargetObject(service);
        ReflectionTestUtils.setField(target, "clock", Clock.fixed(instant, ZoneOffset.UTC));
    }
    MemberPrincipal actor() { return new MemberPrincipal(members.findById(moderator).orElseThrow()); }

    @BeforeEach void setup() {
        base = base.plusSeconds(3600);
        now = base;
        at(now);
        tag = "Px" + UUID.randomUUID().toString().replace("-", "").substring(0, 4);
        moderator = member("MODERADOR", "APROVADO");
        anchor = member("MEMBRO", "APROVADO");
        anchor2 = member("MEMBRO", "APROVADO");
        near = member("MEMBRO", "APROVADO");
        far = member("MEMBRO", "APROVADO");
        pending = member("MEMBRO", "PENDENTE");
        loginAs(moderator);
    }
    @AfterEach void clear() {
        SecurityContextHolder.clearContext();
        at(Instant.now());
    }

    @Test void anchorDllReportGivesPointsToApprovedMembersInRange() {
        var created = service.create(new CreateRequest(List.of(anchor), 25, true, "PB de domingo"), actor());
        assertEquals("WAITING", created.state());
        // So a DLL da referencia recebe o pedido, uma vez.
        assertEquals(List.of(created.id() + "\t900"), service.forGame(nick(anchor).toUpperCase()));
        assertTrue(service.forGame(nick(near)).isEmpty());
        assertTrue(service.forGame(nick(pending)).isEmpty());
        assertThrows(AppException.class, () -> service.report(created.id(), nick(near), nick(far) + "\t10\n"));

        long before = points(near);
        service.report(created.id(), nick(anchor),
                nick(near) + "\t120\n" + nick(far) + "\t901\n" + nick(pending) + "\t5\nForasteiro\t30\n\tx\n" + nick(near) + "\t3\n");
        var done = service.status(created.id());
        assertEquals("DONE", done.state());
        assertEquals(2, done.outsiders());  // Forasteiro e o pendente: nao sao membros aprovados
        var names = done.awarded().stream().map(ProximityPointsService.Awarded::nickname).toList();
        assertTrue(names.containsAll(List.of(nick(anchor), nick(near))));
        assertFalse(names.contains(nick(far)));
        assertFalse(names.contains(nick(pending)));
        assertEquals(before + 25, points(near));
        assertEquals(0, points(far));
        assertEquals(25, points(anchor));
        assertEquals(0, points(pending));
        assertEquals("PROXIMITY", jdbc.queryForObject(
                "SELECT payload->>'source' FROM audit_log WHERE type='POINTS_ADJUSTED' AND target_id=? ORDER BY id DESC LIMIT 1",
                String.class, near));
        assertTrue(jdbc.queryForObject(
                "SELECT payload->>'reason' FROM audit_log WHERE type='POINTS_ADJUSTED' AND target_id=? ORDER BY id DESC LIMIT 1",
                String.class, near).startsWith("PB de domingo (perto de "));
        // Pedido encerrado: nada mais para a DLL, e resposta atrasada e recusada.
        assertTrue(service.forGame(nick(anchor)).isEmpty());
        assertThrows(AppException.class, () -> service.report(created.id(), nick(anchor), nick(far) + "\t1\n"));
        assertEquals(before + 25, points(near));
    }

    @Test void silentAnchorTimesOutAndTheOthersStillCount() {
        var created = service.create(new CreateRequest(List.of(anchor, anchor2), 10, false, null), actor());
        service.report(created.id(), nick(anchor), nick(near) + "\t50\n");
        assertEquals("WAITING", service.status(created.id()).state());
        at(now.plusSeconds(16));
        var done = service.status(created.id());
        assertEquals("DONE", done.state());
        assertTrue(done.anchors().get(0).answered());
        assertFalse(done.anchors().get(1).answered());
        assertEquals(List.of(nick(near)), done.awarded().stream().map(ProximityPointsService.Awarded::nickname).toList());
        assertEquals(10, points(near));
        assertEquals(0, points(anchor));  // referencias de fora nesta vez
    }

    @Test void onlyModeratorsCreate() {
        loginAs(near);
        assertThrows(AppException.class, () -> service.create(new CreateRequest(List.of(anchor), 5, true, null), actor()));
        loginAs(moderator);
        assertThrows(AppException.class, () -> service.create(new CreateRequest(List.of(pending), 5, true, null), actor()));
    }
}
