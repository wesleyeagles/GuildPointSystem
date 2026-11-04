package com.guild.app.resource;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.exception.AppException;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.resource.service.ResourceSpawnService;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ResourceSpawnTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean WebSocketPublisher ws;
    @Autowired ResourceSpawnService service;
    @Autowired MemberRepository members;
    long viewerId, senderId, adminId;
    String viewerNick, senderNick, pendingNick;
    long serial = 900_000_000L + (long) (Math.random() * 1_000_000);

    long member(String nick, String status, String role) {
        jdbc.update("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,?,?,true)", nick, role, status);
        return jdbc.queryForObject("SELECT id FROM member WHERE nickname=?", Long.class, nick);
    }
    static String nick(String prefix) { return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8); }
    MemberPrincipal as(long id) { return new MemberPrincipal(members.findById(id).orElseThrow()); }

    ResultActions send(String character, int map, long since, String body) throws Exception {
        var request = post("/api/resources/game").contentType("text/plain;charset=UTF-8")
            .param("map", String.valueOf(map)).param("since", String.valueOf(since));
        if (character != null) request.param("character", character);
        return mvc.perform(request.content(body.getBytes(StandardCharsets.UTF_8)));
    }
    String ok(String character, int map, long since, String body) throws Exception {
        return send(character, map, since, body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
    long spawns(String name) {
        return jdbc.queryForObject("SELECT count(*) FROM resource_spawn WHERE mob_name=? AND serial>=900000000", Long.class, name);
    }

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM resource_spawn");
        viewerNick = nick("Vie");
        viewerId = member(viewerNick, "APROVADO", "MEMBRO");
        senderNick = nick("Sen");
        senderId = member(senderNick, "APROVADO", "MEMBRO");
        pendingNick = nick("Pen");
        member(pendingNick, "PENDENTE", "MEMBRO");
        adminId = member(nick("Adm"), "APROVADO", "ADMINISTRADOR");
        jdbc.update("INSERT INTO resource_tracker(member_id) VALUES(?)", viewerId);
    }

    @Test void everyoneSendsButOnlyListedSeeDots() throws Exception {
        // Fora da lista: grava, mas não recebe pontos.
        assertEquals("A\t0\n", ok(senderNick, 11, 0, "S\t" + serial + "\t-1113\t-3121\tCactus\n"));
        assertEquals(1, spawns("Cactus"));
        String seen = ok(viewerNick, 11, 0, "");
        assertTrue(seen.startsWith("A\t1\nN\t"), seen);
        assertTrue(seen.contains("\t-1113\t-3121\t1\t"), seen);
        assertTrue(seen.endsWith("\tCactus\n"), seen);
        send(pendingNick, 11, 0, "").andExpect(status().isForbidden());
        send(null, 11, 0, "").andExpect(status().isForbidden());
        send(viewerNick, 12, 0, "").andExpect(status().isBadRequest());   // Coldron não é mapa de recurso
    }

    @Test void sameMobCountsOnceEvenFromAnotherMember() throws Exception {
        String line = "S\t" + serial + "\t10\t20\tSpiky Cactus\n";
        ok(senderNick, 11, 0, line);
        ok(viewerNick, 11, 0, line);
        ok(senderNick, 11, 0, "S\t" + serial + "\t15\t25\tSpiky Cactus\n");   // andou um pouco: mesmo mob
        assertEquals(1, spawns("Spiky Cactus"));
        ok(senderNick, 11, 0, "S\t" + (serial + 1) + "\t10\t20\tSpiky Cactus\n");
        assertEquals(2, spawns("Spiky Cactus"));
    }

    @Test void leveledMobsAreRejectedAndUnknownNamesSurface() throws Exception {
        String unknown = "Odd " + UUID.randomUUID().toString().substring(0, 6);
        ok(senderNick, 11, 0, "S\t" + serial + "\t1\t2\t[43]Turncoat Hunter\nS\t" + (serial + 1) + "\t3\t4\t" + unknown + "\n");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM resource_mob WHERE name='[43]Turncoat Hunter'", Long.class));
        assertNull(jdbc.queryForObject("SELECT tier FROM resource_mob WHERE name=?", Integer.class, unknown));
        assertTrue(ok(viewerNick, 11, 0, "").contains("\t3\t4\t0\t"));                 // desconhecido: grau 0 no jogo

        service.setTier(unknown, 0, as(adminId));                                       // não é recurso: some
        assertFalse(ok(viewerNick, 11, 0, "").contains(unknown));
        ok(senderNick, 11, 0, "S\t" + (serial + 2) + "\t5\t6\t" + unknown + "\n");
        assertEquals(1, spawns(unknown));                                               // e nem grava mais
        assertTrue(service.spawns(11, as(adminId)).stream().noneMatch(s -> s.name().equals(unknown)));

        service.setTier(unknown, 3, as(adminId));
        assertTrue(ok(viewerNick, 11, 0, "").contains("\t3\t4\t3\t"));
    }

    @Test void incrementalDotsAfterSince() throws Exception {
        ok(senderNick, 10, 0, "S\t" + serial + "\t1\t1\tIcy Flower\n");
        String first = ok(viewerNick, 10, 0, "");
        long last = Long.parseLong(first.split("\n")[1].split("\t")[1]);
        assertEquals("A\t1\nN\t" + last + "\n", ok(viewerNick, 10, last, ""));
        ok(senderNick, 10, 0, "S\t" + (serial + 1) + "\t2\t2\tHeart of Ice\n");
        String next = ok(viewerNick, 10, last, "");
        assertTrue(next.contains("\t2\t2\t3\t") && !next.contains("\t1\t1\t1\t"), next);
    }

    @Test void deathGoesToViewersOnceAndToTheSite() throws Exception {
        ok(senderNick, 11, 0, "S\t" + serial + "\t5\t6\tCactus\n");
        long id = jdbc.queryForObject("SELECT id FROM resource_spawn WHERE serial=?", Long.class, serial);
        assertFalse(ok(viewerNick, 11, 0, "").contains("K\t"));
        ok(senderNick, 11, 0, "D\t" + serial + "\tCactus\nD\t" + (serial + 7) + "\tCactus\n");   // o segundo nunca foi visto
        var died = jdbc.queryForObject("SELECT died_at FROM resource_spawn WHERE id=?", java.sql.Timestamp.class, id);
        assertNotNull(died);
        ok(viewerNick, 11, 0, "D\t" + serial + "\tCactus\n");                                     // outro membro: não muda a hora
        assertEquals(died, jdbc.queryForObject("SELECT died_at FROM resource_spawn WHERE id=?", java.sql.Timestamp.class, id));
        String reply = ok(viewerNick, 11, id, "");
        assertTrue(reply.contains("K\t" + id + "\t" + died.toInstant().getEpochSecond() + "\n"), reply);
        assertEquals("A\t0\n", ok(senderNick, 11, 0, ""));                 // fora da lista: nada
        assertFalse(ok(viewerNick, 10, 0, "").contains("K\t"));                                   // outro mapa
        assertEquals(died.toInstant(), service.spawns(11, as(adminId)).get(0).diedAt());
    }

    @Test void mapScaleFromTheGameIsSaved() throws Exception {
        ok(senderNick, 2, 0, "V\t400\t400\t12\t0\t-4000\t4100\t0.05\t0.049\n");
        var crag = service.overview(as(adminId)).maps().stream().filter(m -> m.code() == 2).findFirst().orElseThrow();
        assertEquals(-4000, crag.view().minX());
        assertEquals(0.049, crag.view().scaleY(), 1e-9);
        ok(senderNick, 2, 0, "V\t400\t400\t12\t0\t-4000\t4100\t-1\t0.049\n");          // escala absurda: ignorada
        assertEquals(0.05, service.overview(as(adminId)).maps().stream().filter(m -> m.code() == 2).findFirst().orElseThrow().view().scaleX(), 1e-9);
    }

    @Test void siteIsAdminOnly() throws Exception {
        // Estar na lista libera os pontos no jogo, não a página do site.
        assertFalse(service.overview(as(viewerId)).canView());
        assertThrows(AppException.class, () -> service.spawns(11, as(viewerId)));
        assertThrows(AppException.class, () -> service.updateAccess(List.of(senderId), as(viewerId)));
        var admin = service.updateAccess(List.of(senderId), as(adminId));
        assertTrue(admin.canView() && admin.canConfigure());
        assertEquals(List.of(senderId), admin.trackers().stream().map(t -> t.id()).toList());
        assertTrue(ok(senderNick, 11, 0, "").startsWith("A\t1\n"));     // agora vê no jogo
        assertEquals("A\t0\n", ok(viewerNick, 11, 0, ""));             // saiu da lista
    }
}
