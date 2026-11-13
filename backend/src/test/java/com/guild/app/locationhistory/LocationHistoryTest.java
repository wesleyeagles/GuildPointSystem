package com.guild.app.locationhistory;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.characterstatus.dto.CharacterStatusIngestRequest;
import com.guild.app.characterstatus.service.CharacterStatusService;
import com.guild.app.common.exception.AppException;
import com.guild.app.locationhistory.service.LocationHistoryService;
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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LocationHistoryTest {
    @Autowired LocationHistoryService service;
    @Autowired CharacterStatusService statusService;
    @Autowired MemberRepository members;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean WebSocketPublisher ws;

    final Instant t = Instant.parse("2026-11-11T00:52:00Z");   // 21:52 de Brasília
    long a, b, admin;
    String tag;

    long member(String role, String nick) {
        return jdbc.queryForObject("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,?,'APROVADO',true) RETURNING id",
                Long.class, nick, role);
    }
    void loginAs(long id) {
        var p = new MemberPrincipal(members.findById(id).orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }
    void send(long id, String region, Instant at) {
        service.record(id, "Char" + id, at, "Cora HQ", region, 10, 10, null, "1.36.1");
    }

    @BeforeEach void setup() {
        tag = UUID.randomUUID().toString().substring(0, 6);
        a = member("MEMBRO", "Nico" + tag);
        b = member("MEMBRO", "Paca" + tag);
        admin = member("ADMINISTRADOR", "Adm" + tag);
        jdbc.update("DELETE FROM location_history");
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void ingestKeepsEverySend() {
        String nick = members.findById(a).orElseThrow().getNickname();
        statusService.ingest(new CharacterStatusIngestRequest(9100L, nick, 50, 1f, 10, 10, 1, 1, 1, 1, "Cora HQ", "Cora HQ", 10, 10), "1.36.1");
        statusService.ingest(new CharacterStatusIngestRequest(9100L, nick, 50, 1f, 10, 10, 1, 1, 1, 1, "Cora HQ", "Cora Outpost", 20, 20), "1.36.1");
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM location_history WHERE member_id=?", Integer.class, a));
    }

    @Test void whereWasEveryoneAndOneMember() {
        send(a, "Cora HQ", t.minusSeconds(100));
        send(a, "Cora HQ", t.minusSeconds(40));
        send(a, "Dark Plain", t.plus(Duration.ofMinutes(5)));
        send(b, "Cora Outpost", t.minusSeconds(20));
        send(b, "Cora Outpost", t.minus(Duration.ofMinutes(30)));
        loginAs(admin);

        var all = service.query(t, null, 10).stream().filter(r -> r.nickname().endsWith(tag)).toList();
        assertEquals(2, all.size());
        assertEquals("Cora HQ", all.get(0).region());                 // Nico: último envio antes das 21:52
        assertEquals(t.minusSeconds(40), all.get(0).at());
        assertEquals("Cora Outpost", all.get(1).region());

        var nico = service.query(t, "nico" + tag, 10);
        assertEquals(3, nico.size());                                  // 21:50:20, 21:51:20, 21:57
        assertEquals("Dark Plain", nico.get(2).region());

        jdbc.update("INSERT INTO location_history(member_id,at) VALUES(?,?)", a, java.sql.Timestamp.from(t.minus(Duration.ofDays(4))));
        assertEquals(1, service.cleanup(t));

        loginAs(a);
        assertThrows(AppException.class, () -> service.query(t, null, 10));
    }
}
