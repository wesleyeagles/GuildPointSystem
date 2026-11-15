package com.guild.app.auction;

import com.guild.app.auction.service.AuctionService;
import com.guild.app.common.exception.AppException;
import com.guild.app.websocket.WebSocketPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Só conta bloqueada para venda no site dá lance. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuctionSellBlockTest {

    @Autowired AuctionService service;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean WebSocketPublisher ws;

    long auctionId;
    final LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    long member(LocalDate blockedUntil) {
        return jdbc.queryForObject("INSERT INTO member(nickname,role,status,profile_complete,points,blocked_for_sell_until) "
                + "VALUES(?,'MEMBRO','APROVADO',true,1000,?) RETURNING id", Long.class,
                "Sb" + UUID.randomUUID().toString().replace("-", "").substring(0, 8), blockedUntil);
    }

    @BeforeEach void setup() {
        long creator = member(null);
        long item = jdbc.queryForObject("INSERT INTO item(type,name,image_url,created_by_id) VALUES('MISC','Sb','x.png',?) RETURNING id",
                Long.class, creator);
        auctionId = jdbc.queryForObject("INSERT INTO auction(duration_minutes,status,created_by_id,starts_at,ends_at,current_bid) "
                + "VALUES(60,'OPEN',?,NOW(),NOW() + INTERVAL '1 hour',0) RETURNING id", Long.class, creator);
        jdbc.update("INSERT INTO auction_item(auction_id,item_id,quantity) VALUES(?,?,1)", auctionId, item);
    }

    @Test void notBlockedOrBlockExpiredCannotBid() {
        for (LocalDate until : new LocalDate[] { null, today.minusDays(1) }) {
            long id = member(until);
            var e = assertThrows(AppException.class, () -> service.placeBid(auctionId, 10, id));
            assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
            assertEquals(AuctionService.SELL_BLOCK_REQUIRED, e.getMessage());
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM auction_bid WHERE auction_id=?", Integer.class, auctionId));
    }

    @Test void blockedThroughTodayCanBid() {
        long id = member(today);
        assertEquals(10, service.placeBid(auctionId, 10, id).currentBid());
    }
}
