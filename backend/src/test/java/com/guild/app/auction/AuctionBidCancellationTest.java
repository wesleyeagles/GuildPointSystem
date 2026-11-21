package com.guild.app.auction;

import com.guild.app.auction.repository.AuctionBidRepository;
import com.guild.app.auction.repository.AuctionRepository;
import com.guild.app.auction.service.AuctionService;
import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.enums.AuctionStatus;
import com.guild.app.common.exception.AppException;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.points.service.PointsService;
import com.guild.app.websocket.WebSocketPublisher;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuctionBidCancellationTest {
    @Autowired AuctionService service;
    @Autowired AuctionBidRepository bids;
    @Autowired AuctionRepository auctions;
    @Autowired MemberRepository members;
    @Autowired PointsService points;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean WebSocketPublisher ws;
    long auctionId, itemId;
    MemberPrincipal admin;
    static final List<AuctionStatus> ACTIVE = List.of(AuctionStatus.OPEN, AuctionStatus.DOLE, AuctionStatus.PAUSED, AuctionStatus.TIE_BREAK);

    long member() {
        return jdbc.queryForObject("INSERT INTO member(nickname,role,status,profile_complete,points,blocked_for_sell_until) "
            + "VALUES(?,'MEMBRO','APROVADO',true,1000,CURRENT_DATE + 1) RETURNING id", Long.class,
            "Bc" + UUID.randomUUID().toString().substring(0,8));
    }
    void login(long id) {
        var p = new MemberPrincipal(members.findById(id).orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));
    }
    long lot(int quantity) {
        long id = jdbc.queryForObject("INSERT INTO auction(duration_minutes,status,created_by_id,starts_at,ends_at,current_bid) "
            + "VALUES(60,'OPEN',?,NOW(),NOW()+INTERVAL '1 hour',0) RETURNING id",Long.class,admin.getId());
        jdbc.update("INSERT INTO auction_item(auction_id,item_id,quantity) VALUES(?,?,?)",id,itemId,quantity);
        return id;
    }
    @BeforeEach void setup() {
        long id=member();
        jdbc.update("UPDATE member SET role='ADMINISTRADOR' WHERE id=?",id);
        admin=new MemberPrincipal(members.findById(id).orElseThrow());
        login(id);
        itemId=jdbc.queryForObject("INSERT INTO item(type,name,image_url,created_by_id) VALUES('MISC',?,'x.png',?) RETURNING id",
            Long.class,UUID.randomUUID().toString(),id);
        auctionId=lot(1);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    long bid(long who,long amount) {
        service.placeBid(auctionId,amount,who);
        return bids.findLatestBidId(auctionId);
    }
    long reserve(long id) { return auctions.sumReservedBy(id,ACTIVE,0L); }
    void sql(String query,Object... args) { em.flush(); jdbc.update(query,args); em.clear(); }

    @Test void cancelHighestRestoresPreviousAndKeepsAuditWithoutDebiting() {
        long a=member(), b=member(); long first=bid(a,40), last=bid(b,60);
        var r=service.cancelBid(auctionId,last,"Clique acidental",admin);
        assertEquals(40,r.currentBid()); assertEquals(a,r.leaderId());
        assertEquals(40,reserve(a)); assertEquals(0,reserve(b));
        assertEquals(1000,members.findById(b).orElseThrow().getPoints());
        assertEquals(first,bids.findLatestBidId(auctionId));
        assertEquals(List.of(a),bids.findTiedMemberIds(auctionId,40L));
        assertFalse(bids.findAuctionIdsBidBy(b,ACTIVE).contains(auctionId));
        assertTrue(bids.findOtherAuctionsLeadBy(b,0L,ACTIVE).isEmpty());
        assertTrue(auctions.findIdsLedBy(b,ACTIVE).isEmpty());
        assertEquals(1,bids.findRecent(auctionId,org.springframework.data.domain.PageRequest.of(0,20)).size());
        var history=service.bids(auctionId,0);
        assertEquals(2,history.getTotalElements());
        assertEquals("Clique acidental",history.getContent().getFirst().cancellationReason());
        assertEquals(admin.getNickname(),history.getContent().getFirst().cancelledBy());
        var again=service.cancelBid(auctionId,last,"Duplo clique",admin);
        assertEquals(r.version(),again.version());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE type='AUCTION_BID_CANCELLED' AND payload->>'auctionId'=?",
            Integer.class,Long.toString(auctionId)));
    }
    @Test void cancelOnlyBidReleasesReserveAndAllowsSamePriceAgainWithoutDoubleCounting() {
        long a=member(); long id=bid(a,50);
        assertEquals(0,service.cancelBid(auctionId,id,"Missclick",admin).currentBid());
        assertNull(bids.findLatestBidId(auctionId)); assertEquals(0,reserve(a));
        bid(a,50);
        assertEquals(50,reserve(a));
        var row=auctions.sumReservedByMember(ACTIVE).stream().filter(v->((Number)v[0]).longValue()==a).findFirst().orElseThrow();
        assertEquals(50,((Number)row[1]).longValue());
    }
    @Test void tiedBidCancellationKeepsOtherLeaderAndReserve() {
        long a=member(), b=member();
        sql("UPDATE member SET points=50 WHERE id=?",b);
        bid(a,50); long last=bid(b,50);
        var r=service.cancelBid(auctionId,last,"Missclick",admin);
        assertEquals(50,r.currentBid()); assertEquals(a,r.leaderId()); assertFalse(r.tied());
        assertEquals(0,reserve(b)); assertEquals(50,reserve(a));
    }
    @Test void lowerBidCancellationDoesNotChangeClockOrLeader() {
        long a=member(),b=member();long first=bid(a,40);bid(b,60);
        var before=service.getById(auctionId);
        var r=service.cancelBid(auctionId,first,"Missclick",admin);
        assertEquals(before.endsAt(),r.endsAt());assertEquals(b,r.leaderId());assertEquals(60,r.currentBid());
    }
    @Test void insufficientPreviousBidderCannotBeRestored() {
        long a=member(),b=member();bid(a,500);long last=bid(b,600);
        service.placeBid(lot(2),600,a);
        var e=assertThrows(AppException.class,()->service.cancelBid(auctionId,last,"Missclick",admin));
        assertEquals(HttpStatus.CONFLICT,e.getStatus());
        assertNull(bids.findById(last).orElseThrow().getCancelledAt());
        assertEquals(600,service.getById(auctionId).currentBid());
    }
    @Test void previousBidderLeadingIdenticalLotCannotBeRestored() {
        long a=member(),b=member();bid(a,50);long last=bid(b,60);
        service.placeBid(lot(1),50,a);
        assertThrows(AppException.class,()->service.cancelBid(auctionId,last,"Missclick",admin));
        assertNull(bids.findById(last).orElseThrow().getCancelledAt());
    }
    @Test void rejectsWrongAuctionAndInvalidReason() {
        long last=bid(member(),40);
        assertEquals(HttpStatus.NOT_FOUND,assertThrows(AppException.class,()->service.cancelBid(lot(2),last,"x",admin)).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST,assertThrows(AppException.class,()->service.cancelBid(auctionId,last," ",admin)).getStatus());
        assertThrows(AppException.class,()->service.cancelBid(auctionId,last,"x".repeat(301),admin));
    }
    @Test void onlyAdminCanSeeHistoryOrCancel() {
        long a=member(),last=bid(a,40);login(a);
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(AppException.class,()->service.bids(auctionId,0)).getStatus());
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(AppException.class,()->service.cancelBid(auctionId,last,"x",admin)).getStatus());
    }
    @Test void rejectsFinishedScheduledAndRoulette() {
        long last=bid(member(),40);
        for(String status:List.of("CLOSED","CANCELLED","SCHEDULED","TIE_BREAK")) {
            sql("UPDATE auction SET status=? WHERE id=?",status,auctionId);
            assertEquals(HttpStatus.CONFLICT,assertThrows(AppException.class,()->service.cancelBid(auctionId,last,"x",admin)).getStatus());
        }
        sql("UPDATE auction SET status='OPEN',ends_at=NOW()-INTERVAL '1 minute' WHERE id=?",auctionId);
        assertThrows(AppException.class,()->service.cancelBid(auctionId,last,"x",admin));
    }
    @Test void pausedStaysPausedAndDoleGetsTimeToBidAgain() {
        long a=member(),b=member();bid(a,40);long last=bid(b,60);
        sql("UPDATE auction SET status='PAUSED',paused_remaining_ms=30000 WHERE id=?",auctionId);
        assertEquals(AuctionStatus.PAUSED,service.cancelBid(auctionId,last,"x",admin).status());
        sql("UPDATE auction SET status='DOLE',ends_at=NOW()-INTERVAL '1 second' WHERE id=?",auctionId);
        long remaining=bids.findLatestBidId(auctionId);
        var r=service.cancelBid(auctionId,remaining,"x",admin);
        assertEquals(AuctionStatus.OPEN,r.status());assertTrue(r.endsAt().isAfter(java.time.Instant.now().plusSeconds(8)));
    }
    @Test void fixedDeadlineIsNotExtended() {
        long last=bid(member(),40);
        sql("UPDATE auction SET fixed_deadline=true,ends_at=NOW()+INTERVAL '5 seconds' WHERE id=?",auctionId);
        var end=service.getById(auctionId).endsAt();
        assertEquals(end,service.cancelBid(auctionId,last,"x",admin).endsAt());
    }
}
