package com.guild.app.roster;

import com.guild.app.characterstatus.dto.CharacterStatusIngestRequest;
import com.guild.app.characterstatus.service.CharacterStatusService;
import com.guild.app.websocket.WebSocketPublisher;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Lista de membros no jogo: so Lider/Admin; online, local, parado, visto e ultima EXP de todos os aprovados. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MemberRosterTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CharacterStatusService statusService;
    @Autowired MemberRosterService service;
    @MockitoBean WebSocketPublisher ws;
    @Autowired jakarta.persistence.EntityManager em;

    String tag, admin, online, offline, never, member, pending;
    long gid = 5000;

    String member(String role, String status) {
        String nick = tag + UUID.randomUUID().toString().substring(0, 4);
        jdbc.update("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,?,?,true)", nick, role, status);
        return nick;
    }
    void send(long charGid, String nick, int level, float exp) {
        statusService.ingest(new CharacterStatusIngestRequest(charGid, nick, level, exp, 10, 10, 1, 1, 1, 1,
                "Cora HQ", "Cora HQ", 10, 20), "1.25.0");
    }
    String[] line(String body, String nick) {
        return Arrays.stream(body.split("\n")).filter(l -> l.startsWith(nick + "\t")).findFirst()
                .map(l -> l.split("\t", -1)).orElseThrow();
    }
    String roster(String character) throws Exception {
        // Sem o cache de 5 s entre um envio e outro do teste.
        var target = AopTestUtils.<MemberRosterService>getTargetObject(service);
        var field = MemberRosterService.class.getDeclaredField("cached"); field.setAccessible(true); field.set(target, null);
        return mvc.perform(get("/api/members/game/roster").param("character", character))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @BeforeEach void setup() {
        tag = "Ro" + UUID.randomUUID().toString().substring(0, 4);
        admin = member("ADMINISTRADOR", "APROVADO");
        online = member("MEMBRO", "APROVADO");
        offline = member("MEMBRO", "APROVADO");
        never = member("MEMBRO", "APROVADO");
        member = member("MODERADOR", "APROVADO");
        pending = member("MEMBRO", "PENDENTE");
        long onlineGid = gid++;
        send(onlineGid, online, 50, 10f);
        send(onlineGid, online, 50, 12.5f);  // ganhou EXP
        long offGid = gid++;
        send(offGid, offline, 40, 5f);
        em.flush();
        jdbc.update("UPDATE character_status SET observed_at = now() - interval '2 hours', location_at = now() - interval '2 hours' WHERE gid=?", offGid);
        em.clear();
    }

    @Test void onlyLeadersAndAdmins() throws Exception {
        mvc.perform(get("/api/members/game/roster").param("character", member)).andExpect(status().isForbidden());
        mvc.perform(get("/api/members/game/roster")).andExpect(status().isForbidden());
        roster(admin);
    }

    @Test void listsEveryApprovedMember() throws Exception {
        String body = roster(admin);
        String[] on = line(body, online);
        assertEquals("1", on[1]);
        assertEquals("Cora HQ", on[2]);
        assertTrue(Long.parseLong(on[4]) >= 0);       // parado
        assertTrue(Long.parseLong(on[6]) >= 0);       // ganhou EXP agora
        String[] off = line(body, offline);
        assertEquals("0", off[1]);
        assertEquals("", off[2]);                     // sem local quando offline
        assertTrue(Long.parseLong(off[5]) >= 7000);   // visto ha ~2 h
        assertEquals("-1", off[6]);                   // nunca ganhou EXP (um envio so)
        String[] nv = line(body, never);
        assertEquals("-1", nv[5]);
        assertFalse(body.contains(pending + "	"));  // so aprovados
        // Online antes dos offline.
        assertTrue(body.indexOf(online + "\t") < body.indexOf(offline + "\t"));
        assertTrue(body.indexOf(offline + "\t") < body.indexOf(never + "\t"));
    }

    @Test void sameNickTwiceIsListedOnce() throws Exception {
        String twin = tag + "Twin";
        jdbc.update("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,?,?,true)", twin, "MEMBRO", "APROVADO");
        jdbc.update("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,?,?,true)", twin, "MEMBRO", "APROVADO");
        send(gid++, twin, 50, 10f);
        String body = roster(admin);
        assertEquals(1, Arrays.stream(body.split("\n")).filter(l -> l.startsWith(twin + "\t")).count());
        assertEquals("1", line(body, twin)[1]);  // fica a linha online
    }

    @Test void oldDllWithoutCoordinatesIsNeverAfk() throws Exception {
        String old = member("MEMBRO", "APROVADO");
        statusService.ingest(new CharacterStatusIngestRequest(gid++, old, 50, 1f, 10, 10, 1, 1, 1, 1,
                "Cora HQ", "Cora HQ", null, null), "1.25.0");
        assertEquals("-1", line(roster(admin), old)[4]);
    }

    @Test void deathDoesNotCountAsExp() throws Exception {
        long g = gid++;
        String dead = member("MEMBRO", "APROVADO");
        send(g, dead, 60, 50f);
        send(g, dead, 60, 49f);  // morreu
        assertEquals("-1", line(roster(admin), dead)[6]);
    }
}
