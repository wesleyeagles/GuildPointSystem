package com.guild.app.pbauto;

import com.guild.app.characterstatus.dto.CharacterStatusIngestRequest;
import com.guild.app.characterstatus.service.CharacterStatusService;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.pbauto.service.PbAutoService;
import com.guild.app.shout.service.ShoutService;
import com.guild.app.websocket.WebSocketPublisher;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PbAutoTest {
    @Autowired PbAutoService service;
    @Autowired ShoutService shouts;
    @Autowired CharacterStatusService statusService;
    @Autowired MemberRepository members;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @MockitoBean WebSocketPublisher ws;

    /** 01:52 de Brasília (UTC−3) de 11/11/2026: horário do RG do PB Colônia das 02:00. */
    final Instant rg = Instant.parse("2026-11-11T04:52:00Z");
    long a, b, c, admin;
    long gid = 7000;

    long member(String role) {
        return jdbc.queryForObject("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,?,'APROVADO',true) RETURNING id",
                Long.class, "Pb" + UUID.randomUUID().toString().replace("-", "").substring(0, 8), role);
    }
    String nick(long id) { return members.findById(id).orElseThrow().getNickname(); }
    long points(long id) { em.flush(); return jdbc.queryForObject("SELECT points FROM member WHERE id=?", Long.class, id); }
    void clock(Instant at) { service.useClock(Clock.fixed(at, ZoneOffset.UTC)); }
    void place(long id, String map, String region, Instant at) {
        statusService.ingest(new CharacterStatusIngestRequest(gid++, nick(id), 50, 1f, 10, 10, 1, 1, 1, 1, map, region, 10, 10), "1.36.0");
        jdbc.update("UPDATE character_status SET location_at=? WHERE member_id=?", Timestamp.from(at), id);
    }

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM pb_auto_award");
        jdbc.update("DELETE FROM pb_auto_presence");
        jdbc.update("DELETE FROM pb_auto_run");
        a = member("MEMBRO");
        b = member("MEMBRO");
        c = member("MEMBRO");
        admin = member("ADMINISTRADOR");
    }
    @AfterEach void clear() { service.useClock(Clock.systemUTC()); }

    @Test void shoutsAt0140_0145_0150() {
        clock(rg.minus(Duration.ofMinutes(12)).plusSeconds(10));   // 01:40:10
        service.tick();
        assertEquals("RG Base Avançada, Pontos de RG as 01:52", shouts.forGame(nick(a)).orElseThrow().message());
        long first = shouts.forGame(nick(a)).orElseThrow().id();
        service.tick();                                            // mesmo minuto: não repete
        assertEquals(first, shouts.forGame(nick(a)).orElseThrow().id());
        clock(rg.minus(Duration.ofMinutes(7)).plusSeconds(5));     // 01:45:05
        service.tick();
        assertNotEquals(first, shouts.forGame(nick(a)).orElseThrow().id());
    }

    @Test void rgAt0152ForWhoIsInCoraOutpost() {
        place(a, "Cora HQ", "Cora Outpost", rg.minusSeconds(40));
        place(b, "Cora HQ", "Cora HQ", rg.minusSeconds(40));               // outra região
        place(c, "Cora HQ", "Cora Outpost", rg.minus(Duration.ofMinutes(5)));  // local velho: saiu
        long beforeA = points(a), beforeB = points(b), beforeC = points(c);
        clock(rg.minusSeconds(20));
        service.tick();                                                    // 01:51:40: ainda não
        assertEquals(beforeA, points(a));
        clock(rg.plusSeconds(10));
        service.tick();
        service.tick();
        assertEquals(beforeA + 2, points(a));
        assertEquals(beforeB, points(b));
        assertEquals(beforeC, points(c));
    }

    @Test void pbColoniaFor0152To0205InShadowWoodOrRockCavern() {
        jdbc.update("INSERT INTO objective(name,points,type,created_by_id) VALUES('PB Colônia',20,'NORMAL',?)", admin);
        service.record(a, nick(a), "Haram", "Shadow Wood", rg.plusSeconds(60));       // 01:53
        service.record(b, nick(b), "Haram", "Rock Cavern", rg.plus(Duration.ofMinutes(12)));  // 02:04
        service.record(c, nick(c), "Haram", "Shadow Wood", rg.minusSeconds(60));      // 01:51: antes da janela
        service.record(c, nick(c), "Haram", "Haram Stock.", rg.plusSeconds(60));      // outra região
        service.record(c, nick(c), "Haram", "Rock Cavern", rg.plus(Duration.ofMinutes(14)));  // 02:06: depois
        long beforeA = points(a), beforeB = points(b), beforeC = points(c);
        clock(rg.plus(Duration.ofMinutes(12)).plusSeconds(50));                       // 02:04:50
        service.tick();
        assertEquals(beforeA, points(a));
        clock(rg.plus(Duration.ofMinutes(13)).plusSeconds(10));                       // 02:05:10
        service.tick();
        service.tick();
        assertEquals(beforeA + 20, points(a));
        assertEquals(beforeB + 20, points(b));
        assertEquals(beforeC, points(c));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM event_claim WHERE member_id=?", Integer.class, a));
    }

    @Test void pb2200InDarkPlainOrHunterCave() {
        Instant rg22 = Instant.parse("2026-11-12T00:52:00Z");                      // 21:52 de 11/11 em Brasília
        jdbc.update("INSERT INTO objective(name,points,type,created_by_id) VALUES('PB HQ',5,'NORMAL',?)", admin);
        clock(rg22.minus(Duration.ofMinutes(12)).plusSeconds(10));                // 21:40:10
        service.tick();
        assertEquals("RG Base Avançada, Pontos de RG as 21:52", shouts.forGame(nick(a)).orElseThrow().message());
        place(a, "Cora HQ", "Cora Outpost", rg22.minusSeconds(30));
        long beforeA = points(a), beforeB = points(b), beforeC = points(c);
        clock(rg22.plusSeconds(10));
        service.tick();
        assertEquals(beforeA + 2, points(a));
        service.record(b, nick(b), "Cora HQ", "Dark Plain", rg22.plusSeconds(60));
        service.record(c, nick(c), "Cora HQ", "Hunter Cave", rg22.plus(Duration.ofMinutes(5)));
        service.record(a, nick(a), "Haram", "Shadow Wood", rg22.plusSeconds(60));   // região do PB das 02:00
        clock(rg22.plus(Duration.ofMinutes(13)).plusSeconds(10));                 // 22:05:10
        service.tick();
        assertEquals(beforeA + 2, points(a));
        assertEquals(beforeB + 5, points(b));
        assertEquals(beforeC + 5, points(c));
    }

    @Test void withoutTheObjectiveItWaitsAndPaysWhenCreated() {
        service.record(a, nick(a), "Haram", "Shadow Wood", rg.plusSeconds(60));
        long before = points(a);
        clock(rg.plus(Duration.ofMinutes(13)).plusSeconds(10));
        service.tick();
        assertEquals(before, points(a));
        jdbc.update("INSERT INTO objective(name,points,type,created_by_id) VALUES('PB Colonia',20,'NORMAL',?)", admin);
        clock(rg.plus(Duration.ofMinutes(14)));
        service.tick();
        assertEquals(before + 20, points(a));
    }
}
