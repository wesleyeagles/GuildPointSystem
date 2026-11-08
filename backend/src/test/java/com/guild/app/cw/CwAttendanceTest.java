package com.guild.app.cw;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.exception.AppException;
import com.guild.app.cw.service.CwAttendanceService;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.websocket.WebSocketPublisher;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CwAttendanceTest {
    @Autowired CwAttendanceService service;
    @Autowired MemberRepository members;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean WebSocketPublisher ws;

    static final String CRAG = "Crag Mind Field";
    /** 14:00 em Moscou (UTC+3): começo da CW2. */
    final Instant t0 = Instant.parse("2026-11-08T11:00:00Z");
    long a, admin;

    long member(String role) {
        return jdbc.queryForObject("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,?,'APROVADO',true) RETURNING id",
                Long.class, "Cw" + UUID.randomUUID().toString().replace("-", "").substring(0, 8), role);
    }
    void loginAs(long id) {
        var p = new MemberPrincipal(members.findById(id).orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }
    Instant at(int seconds) { return t0.plusSeconds(seconds); }
    /** Andando: parado desde agora. */
    void moving(int seconds, String map) { service.record(a, "Fulano", map, at(seconds), at(seconds)); }
    int[] times() {
        return jdbc.queryForObject("SELECT active_seconds, afk_seconds FROM cw_attendance WHERE member_id=?",
                (r, n) -> new int[]{r.getInt(1), r.getInt(2)}, a);
    }

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM cw_attendance");
        a = member("MEMBRO");
        admin = member("ADMINISTRADOR");
        service.useClock(Clock.fixed(at(3600), ZoneOffset.UTC));
    }
    @AfterEach void clear() {
        SecurityContextHolder.clearContext();
        service.useClock(Clock.systemUTC());
    }

    @Test void windowsAreServerTime() {
        assertEquals(2, CwAttendanceService.slotAt(t0).orElseThrow().window().slot());
        assertTrue(CwAttendanceService.slotAt(t0.minusSeconds(1)).isEmpty());                    // 13:59 Moscou
        assertEquals(1, CwAttendanceService.slotAt(Instant.parse("2026-11-08T04:59:00Z")).orElseThrow().window().slot());
        var cw3 = CwAttendanceService.slotAt(Instant.parse("2026-11-08T20:59:00Z")).orElseThrow();  // 23:59 Moscou
        assertEquals(3, cw3.window().slot());
        assertEquals(LocalDate.of(2026, 11, 8), cw3.date());
        assertEquals(Instant.parse("2026-11-08T21:00:00Z"), cw3.endsAt());
        assertTrue(CwAttendanceService.slotAt(Instant.parse("2026-11-08T21:00:00Z")).isEmpty());   // 00:00 Moscou
    }

    @Test void countsTimeBetweenSendsInTheMap() {
        moving(0, CRAG);
        moving(60, CRAG);
        moving(120, CRAG);
        assertArrayEquals(new int[]{120, 0}, times());
    }

    @Test void afkTimeIsSeparate() {
        for (int s = 0; s <= 300; s += 60) service.record(a, "Fulano", CRAG, at(0), at(s));   // parado desde 0
        // 0-180 s ainda não é AFK (3 min); 240 e 300 chegam com mais de 3 min parado.
        assertArrayEquals(new int[]{180, 120}, times());
    }

    @Test void timeOutsideTheMapAndLongGapsDoNotCount() {
        moving(0, CRAG);
        moving(60, "Cora HQ");       // voltou para a base
        moving(120, CRAG);           // 60-120 foi fora: não conta
        moving(180, CRAG);           // conta 60
        moving(500, CRAG);           // 320 s sem envio (DLL fechou?): não conta
        moving(560, CRAG);           // conta 60
        assertArrayEquals(new int[]{120, 0}, times());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM cw_attendance WHERE member_id=? AND NOT in_map", Integer.class, a));
    }

    @Test void outsideTheCwNothingIsRecorded() {
        service.record(a, "Fulano", CRAG, null, t0.minusSeconds(60));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM cw_attendance", Integer.class));
    }

    @Test void overviewIsForAdministrators() {
        moving(0, CRAG);
        moving(60, CRAG);
        loginAs(a);
        assertThrows(AppException.class, () -> service.overview(7));
        loginAs(admin);
        var overview = service.overview(7);
        assertEquals(1, overview.cws().size());
        var cw = overview.cws().get(0);
        assertEquals("CW2", cw.label());
        assertEquals(t0, cw.startsAt());
        assertEquals(1, cw.members().size());
        assertEquals(60, cw.members().get(0).activeSeconds());
        assertEquals(at(60), cw.members().get(0).lastSeenAt());
    }
}
