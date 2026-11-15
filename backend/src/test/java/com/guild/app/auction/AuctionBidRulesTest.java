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

/** Regras do lance: conta bloqueada para venda no site e mínimo de +10 sobre o lance atual. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuctionBidRulesTest {

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

    @Test void newBidMustBeAtLeastTenOverTheCurrentOne() {
        long first = member(today), second = member(today);
        assertThrows(AppException.class, () -> service.placeBid(auctionId, 9, first));   // 1º lance: mínimo 10
        service.placeBid(auctionId, 10, first);
        var e = assertThrows(AppException.class, () -> service.placeBid(auctionId, 19, second));
        assertTrue(e.getMessage().contains("lance mínimo é 20"), e.getMessage());
        assertEquals(20, service.placeBid(auctionId, 20, second).currentBid());
    }

    @Test void allInHasNoMinimumIncrement() {
        long first = member(today), poor = member(today), rich = member(today);
        jdbc.update("UPDATE member SET points=51 WHERE id=?", poor);
        service.placeBid(auctionId, 50, first);
        assertEquals(51, service.placeBid(auctionId, 51, poor).currentBid());          // tudo o que tem: passa
        assertThrows(AppException.class, () -> service.placeBid(auctionId, 55, rich));  // tem 1000: precisa de 61
        long tiny = member(today);
        jdbc.update("UPDATE member SET points=5 WHERE id=?", tiny);
        long other = jdbc.queryForObject("INSERT INTO auction(duration_minutes,status,created_by_id,starts_at,ends_at,current_bid) "
                + "SELECT 60,'OPEN',created_by_id,NOW(),NOW() + INTERVAL '1 hour',0 FROM auction WHERE id=? RETURNING id", Long.class, auctionId);
        assertEquals(5, service.placeBid(other, 5, tiny).currentBid());                // 1º lance all-in abaixo de 10
    }

    @Test void tyingWithAllInStillWorks() {
        long first = member(today), poor = member(today);
        jdbc.update("UPDATE member SET points=30 WHERE id=?", poor);
        service.placeBid(auctionId, 30, first);
        var r = service.placeBid(auctionId, 30, poor);                                    // empata com tudo
        assertTrue(r.tied());
    }

    long lot(long itemId, int quantity) {
        long id = jdbc.queryForObject("INSERT INTO auction(duration_minutes,status,created_by_id,starts_at,ends_at,current_bid) "
                + "SELECT 60,'OPEN',created_by_id,NOW(),NOW() + INTERVAL '1 hour',0 FROM auction WHERE id=? RETURNING id", Long.class, auctionId);
        jdbc.update("INSERT INTO auction_item(auction_id,item_id,quantity) VALUES(?,?,?)", id, itemId, quantity);
        return id;
    }

    @Test void winningOneLotBlocksBiddingOnAnotherIdenticalLot() {
        long item = jdbc.queryForObject("SELECT item_id FROM auction_item WHERE auction_id=?", Long.class, auctionId);
        long same = lot(item, 1), otherQuantity = lot(item, 10);
        long ana = member(today), bia = member(today);
        service.placeBid(auctionId, 50, ana);                                       // Ana ganhando o lote "item x1"
        var e = assertThrows(AppException.class, () -> service.placeBid(same, 10, ana));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals(AuctionService.sameLotMessage(auctionId), e.getMessage());
        assertEquals(10, service.placeBid(otherQuantity, 10, ana).currentBid());   // mesma coisa, outra quantidade: pode
        service.placeBid(auctionId, 60, bia);                                       // Bia passou a Ana no primeiro
        assertEquals(10, service.placeBid(same, 10, ana).currentBid());            // agora a Ana pode no outro
        // Empatada (all-in) tambem conta como ganhando.
        long cara = member(today);
        jdbc.update("UPDATE member SET points=60 WHERE id=?", cara);
        assertTrue(service.placeBid(auctionId, 60, cara).tied());
        assertThrows(AppException.class, () -> service.placeBid(same, 20, cara));
    }

    @Test void blockedThroughTodayCanBid() {
        long id = member(today);
        assertEquals(10, service.placeBid(auctionId, 10, id).currentBid());
    }
}
