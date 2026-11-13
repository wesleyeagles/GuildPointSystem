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
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    @Test void towerRiftMetalGoesToTheGameAsTier4() throws Exception {
        ok(senderNick, 2, 0, "S\t" + serial + "\t-700\t-1800\tTower Rift Metal\n");
        String reply = ok(viewerNick, 2, 0, "");
        assertTrue(reply.contains("\t-700\t-1800\t4\t") && reply.contains("Tower Rift Metal"), reply);
        assertEquals(4, service.spawns(2, as(adminId)).get(0).tier());
        assertThrows(AppException.class, () -> service.setTier("Tower Rift Metal", 5, as(adminId)));
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

    @Test void historyReturnsSerialsAndPaginatesWithoutMixingMaps() throws Exception {
        ok(senderNick, 11, 0, "S\t" + serial + "\t10\t20\tSpiky Cactus\nS\t" + (serial+1) + "\t10\t20\tSpiky Cactus\n");
        ok(senderNick, 10, 0, "S\t" + (serial+2) + "\t10\t20\tIcy Flower\n");
        var first = service.history(11, 0, 1, as(adminId));
        assertTrue(first.hasMore());
        assertEquals(serial, first.entries().get(0).serial());
        var second = service.history(11, first.nextCursor(), 1, as(adminId));
        assertFalse(second.hasMore());
        assertEquals(serial+1, second.entries().get(0).serial());
        assertNotEquals(first.entries().get(0).id(), second.entries().get(0).id());
        var empty = service.history(11, second.nextCursor(), 1, as(adminId));
        assertEquals(second.nextCursor(), empty.nextCursor());
        assertTrue(empty.entries().isEmpty());
        mvc.perform(get("/api/resources/history").with(user(as(adminId))).param("map","11").param("limit","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("entries[0].serial").value(serial))
            .andExpect(jsonPath("hasMore").value(true));
    }

    @Test void historyCatchesLateDeathOfAnOlderSpawnAfterCursorAdvanced() throws Exception {
        ok(senderNick, 11, 0, "S\t" + serial + "\t1\t2\tCactus\n");
        long id = service.history(11, 0, 100, as(adminId)).entries().get(0).id();
        ok(senderNick, 11, 0, "S\t" + (serial+1) + "\t3\t4\tCactus\n");
        long cursor = service.history(11, 0, 100, as(adminId)).nextCursor();
        ok(senderNick, 11, 0, "D\t" + serial + "\tCactus\n");
        var late = service.history(11, cursor, 100, as(adminId));
        assertEquals(1, late.entries().size());
        assertEquals(id, late.entries().get(0).id());
        assertEquals("DIED", late.entries().get(0).kind());
        assertNotNull(late.entries().get(0).diedAt());
        ok(viewerNick, 11, 0, "D\t" + serial + "\tCactus\n");
        assertTrue(service.history(11, late.nextCursor(), 100, as(adminId)).entries().isEmpty());
        // Historical deaths remain available even when the game's short-lived notification has expired.
        jdbc.update("UPDATE resource_spawn SET died_at=now()-interval '1 day' WHERE id=?", id);
        assertFalse(ok(viewerNick, 11, 0, "").contains("K\t" + id + "\t"));
        assertNotNull(service.history(11, cursor, 100, as(adminId)).entries().get(0).diedAt());
    }

    @Test void historyIsAdminOnlyAndRejectsInvalidPaging() throws Exception {
        mvc.perform(get("/api/resources/history").param("map","11")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/resources/history").with(user(as(viewerId))).param("map","11"))
            .andExpect(status().isForbidden());
        jdbc.update("UPDATE member SET suspended_at=now() WHERE id=?", adminId);
        assertThrows(AppException.class, () -> service.history(11,0,100,as(adminId)));
        long activeAdminId = member(nick("Active"), "APROVADO", "ADMINISTRADOR");
        for (int limit : new int[]{0,1001})
            mvc.perform(get("/api/resources/history").with(user(as(activeAdminId))).param("map","11").param("limit",String.valueOf(limit)))
                .andExpect(status().isBadRequest());
        assertThrows(AppException.class, () -> service.history(12,0,100,as(activeAdminId)));
        assertThrows(AppException.class, () -> service.history(11,-1,100,as(activeAdminId)));
    }

    @Test void historyDoesNotCreateEventsOnDuplicateSightingOrEmptyPoll() throws Exception {
        String sighting="S\t" + serial + "\t1\t2\tCactus\n";
        ok(senderNick,11,0,sighting);
        long cursor=service.history(11,0,100,as(adminId)).nextCursor();
        ok(viewerNick,11,0,sighting);
        ok(viewerNick,11,0,"");
        assertTrue(service.history(11,cursor,100,as(adminId)).entries().isEmpty());
    }

    @Test void historyCursorWaitsForEarlierWriterToCommit() throws Exception {
        ok(senderNick,11,0,"S\t" + serial + "\t1\t2\tCactus\n");
        try (var connection = Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            connection.setAutoCommit(false);
            try (var sql = connection.createStatement()) {
                sql.execute("SET LOCAL lock_timeout='200ms'");
                // A different map remains writable while this transaction holds map 11.
                assertEquals(1, sql.executeUpdate("UPDATE resource_history_cursor SET revision=revision+1 WHERE map_code=10"));
                var blocked = assertThrows(java.sql.SQLException.class,
                    () -> sql.executeUpdate("UPDATE resource_history_cursor SET revision=revision+1 WHERE map_code=11"));
                assertEquals("55P03", blocked.getSQLState());
            } finally {
                connection.rollback();
            }
        }
    }

    @Test void historyMigrationBackfillsExistingRowsAndOldDeaths() throws Exception {
        try (var connection = Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            connection.setAutoCommit(false);
            try {
                String schema="history_test_"+UUID.randomUUID().toString().replace("-", "");
                var sql=connection.createStatement();
                sql.execute("CREATE SCHEMA "+schema);
                sql.execute("SET LOCAL search_path TO "+schema);
                sql.execute("CREATE TABLE resource_spawn(id BIGINT PRIMARY KEY,map_code INT,died_at TIMESTAMPTZ)");
                sql.execute("INSERT INTO resource_spawn VALUES(20,11,now()-interval '2 days'),(30,11,null),(40,2,null)");
                sql.execute(new org.springframework.core.io.ClassPathResource("db/migration/V122__resource_history.sql")
                    .getContentAsString(StandardCharsets.UTF_8));
                var result=sql.executeQuery("SELECT revision FROM resource_history_cursor WHERE map_code=11");
                assertTrue(result.next()); assertEquals(2,result.getLong(1));
                result=sql.executeQuery("SELECT count(*) FROM resource_spawn_change h JOIN resource_spawn s ON s.id=h.spawn_id WHERE h.kind='SNAPSHOT' AND s.died_at IS NOT NULL");
                assertTrue(result.next()); assertEquals(1,result.getInt(1));
                sql.execute("UPDATE resource_spawn SET died_at=now() WHERE id=30");
                result=sql.executeQuery("SELECT kind FROM resource_spawn_change WHERE map_code=11 AND revision=3");
                assertTrue(result.next()); assertEquals("DIED",result.getString(1));
            } finally {
                connection.rollback();
            }
        }
    }

    @Test void respawnStatsPerTier() {
        // Três Spiky Cactus (grau 2) a cada 20 min, na mesma hora; um Cactus (grau 1) em outra hora.
        java.time.Instant base = java.time.Instant.now().minus(java.time.Duration.ofHours(3)).truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        for (int i = 0; i < 3; i++)
            jdbc.update("INSERT INTO resource_spawn(map_code,mob_name,serial,x,z,found_at) VALUES(11,'Spiky Cactus',?,0,0,?)",
                serial + i, java.sql.Timestamp.from(base.plusSeconds(i * 1200L + 60)));
        jdbc.update("INSERT INTO resource_spawn(map_code,mob_name,serial,x,z,found_at) VALUES(11,'Cactus',?,0,0,?)",
            serial + 9, java.sql.Timestamp.from(base.plusSeconds(3600 + 120)));
        var stats = service.stats(11, 7, as(adminId));
        assertEquals(2, stats.observedHours());
        var g2 = stats.tiers().get(1);
        assertEquals(2, g2.tier());
        assertEquals(3, g2.total());
        assertEquals(1.5, g2.perObservedHour(), 1e-9);
        assertEquals(50.0, g2.expectedPerHour());
        assertEquals(1200L, g2.medianGapSeconds());
        assertEquals(g2.lastSeenAt().plusSeconds(1200), g2.nextLikelyAt());
        assertEquals(1, g2.byMinute()[0]);   // minuto 1, 21 e 41
        assertEquals(1, g2.byMinute()[4]);
        assertEquals(1, g2.byMinute()[8]);
        assertNull(stats.tiers().get(2).lastSeenAt());
        assertThrows(AppException.class, () -> service.stats(11, 7, as(viewerId)));
    }
}
