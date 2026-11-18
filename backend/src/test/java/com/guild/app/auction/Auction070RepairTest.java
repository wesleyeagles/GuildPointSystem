package com.guild.app.auction;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class Auction070RepairTest {
    @Autowired JdbcTemplate jdbc;

    @Test void reopensAndRefundsExactlyOnceWhilePreservingBids() throws Exception {
        long player = jdbc.queryForObject(
            "INSERT INTO member(nickname,role,status,profile_complete,points) VALUES(?,'MEMBRO','APROVADO',true,310) RETURNING id",
            Long.class, "repair-" + UUID.randomUUID());
        long item = jdbc.queryForObject(
            "INSERT INTO item(type,name,created_by_id) VALUES('MISC','(1GR) Heart of Caves Relic',?) RETURNING id", Long.class, player);
        long lot = jdbc.queryForObject(
            "INSERT INTO auction(duration_minutes,status,created_by_id,created_at,ends_at,current_bid,winner_id) "
            + "VALUES(720,'CLOSED',?,'2026-11-18T02:27:55Z','2026-11-18T17:27:55Z',90,?) RETURNING id",
            Long.class, player, player);
        jdbc.update("INSERT INTO auction_item(auction_id,item_id,quantity) VALUES(?,?,1)", lot, item);
        jdbc.update("INSERT INTO auction_bid(auction_id,member_id,amount) VALUES(?,?,90)", lot, player);
        jdbc.update("INSERT INTO points_adjustment(member_id,actor_id,amount,modality,reason) VALUES(?,?,-90,'LEILAO',?)",
            player, player, "Auction win #" + lot);
        jdbc.update("INSERT INTO audit_log(type,payload) VALUES('AUCTION_CLOSED',jsonb_build_object('auctionId',?::bigint,'winnerId',?::bigint,'charged',90))",
            lot, player);
        var deadline = Instant.now().plusSeconds(3600);
        var script = new ClassPathResource("db/migration/V135__reopen_auction_070.sql")
            .getContentAsString(StandardCharsets.UTF_8)
            .replace("target_id constant bigint := 70", "target_id constant bigint := " + lot)
            .replace("'2026-11-18 21:50:00 America/Sao_Paulo'", "'" + deadline + "'")
            .replace("nickname = 'CKP'", "id = " + player);
        jdbc.execute(script);
        jdbc.execute(script);
        assertEquals("OPEN", jdbc.queryForObject("SELECT status FROM auction WHERE id=?", String.class, lot));
        assertNull(jdbc.queryForObject("SELECT winner_id FROM auction WHERE id=?", Long.class, lot));
        assertEquals(400L, jdbc.queryForObject("SELECT points FROM member WHERE id=?", Long.class, player));
        assertEquals(90L, jdbc.queryForObject("SELECT current_bid FROM auction WHERE id=?", Long.class, lot));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM auction_bid WHERE auction_id=?", Long.class, lot));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM points_adjustment WHERE member_id=? AND amount=90", Long.class, player));
        assertFalse(jdbc.queryForObject("SELECT fixed_deadline FROM auction WHERE id=?", Boolean.class, lot));
    }
}
