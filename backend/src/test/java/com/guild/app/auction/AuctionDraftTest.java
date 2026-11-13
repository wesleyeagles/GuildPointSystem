package com.guild.app.auction;

import com.guild.app.auction.dto.CreateAuctionRequest;
import com.guild.app.auction.entity.Auction;
import com.guild.app.auction.repository.*;
import com.guild.app.auction.service.AuctionService;
import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.enums.*;
import com.guild.app.common.exception.AppException;
import com.guild.app.item.entity.Item;
import com.guild.app.item.repository.ItemRepository;
import com.guild.app.log.service.AuditLogService;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.websocket.WebSocketPublisher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionDraftTest {
    @Mock AuctionRepository auctions;
    @Mock AuctionItemRepository auctionItems;
    @Mock ItemRepository items;
    @Mock MemberRepository members;
    @Mock AuditLogService audit;
    @Mock WebSocketPublisher websocket;
    @InjectMocks AuctionService service;
    MemberPrincipal actor;
    @BeforeEach void authenticate() {
        var member = new Member(); member.setId(1L); member.setRole(Role.ADMINISTRADOR);
        actor = new MemberPrincipal(member);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, actor.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    CreateAuctionRequest request(Instant end, Instant start, Boolean later) {
        return new CreateAuctionRequest(15, List.of(new CreateAuctionRequest.AuctionItemEntry(7L, 3)), start, later, end);
    }
    void arrangeSave() {
        when(auctions.save(any())).thenAnswer(call -> { Auction a = call.getArgument(0); a.setId(99L); return a; });
        when(items.findById(7L)).thenReturn(Optional.of(new Item()));
    }
    @Test void exactDeadlineCreatesFreshAuctionWithoutBids() {
        arrangeSave(); var end = Instant.now().plusSeconds(300);
        var result = service.create(request(end, null, false), actor);
        assertEquals(end, result.endsAt()); assertEquals(AuctionStatus.OPEN, result.status());
        assertEquals(0L, result.currentBid()); assertNull(result.winnerId());
        verify(auctionItems).save(argThat(a -> a.getQuantity() == 3));
    }
    @Test void rejectsExpiredTooDistantOrScheduledDeadlineBeforeWriting() {
        var end = Instant.now().plusSeconds(300);
        for (var request : List.of(request(Instant.now().minusSeconds(1), null, false),
                request(Instant.now().plusSeconds(86410), null, false), request(end, null, true),
                request(end, Instant.now().plusSeconds(60), false))) {
            assertThrows(AppException.class, () -> service.create(request, actor));
        }
        verifyNoInteractions(auctions, auctionItems);
    }
    @Test void existingManualAndScheduledStartsRemainSupported() {
        arrangeSave(); var start = Instant.now().plusSeconds(3600);
        var scheduled = service.create(request(null, start, false), actor);
        assertEquals(AuctionStatus.SCHEDULED, scheduled.status()); assertEquals(start, scheduled.startsAt());
        assertEquals(start.plusSeconds(900), scheduled.endsAt());
        var manual = service.create(request(null, null, true), actor);
        assertEquals(AuctionStatus.SCHEDULED, manual.status()); assertNull(manual.startsAt());
    }
    @Test void nonAdminCannotCreateCopy() {
        var member = new Member(); member.setRole(Role.MEMBRO); var principal = new MemberPrincipal(member);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        assertThrows(AppException.class, () -> service.create(request(Instant.now().plusSeconds(300), null, false), principal));
        verifyNoInteractions(auctions);
    }
}
