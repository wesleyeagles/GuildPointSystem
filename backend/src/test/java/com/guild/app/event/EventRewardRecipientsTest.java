package com.guild.app.event;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.enums.Role;
import com.guild.app.common.exception.AppException;
import com.guild.app.event.entity.EventClaim;
import com.guild.app.event.entity.GuildEvent;
import com.guild.app.event.repository.EventClaimRepository;
import com.guild.app.event.service.EventService;
import com.guild.app.member.entity.Member;
import com.guild.app.objective.entity.Objective;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventRewardRecipientsTest {
    @Mock EventClaimRepository claims;
    @InjectMocks EventService service;
    void authenticate(Role role) {
        var member=new Member(); member.setId(1L); member.setNickname("Admin"); member.setRole(role);
        var principal=new MemberPrincipal(member);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
    }
    @BeforeEach void admin() { authenticate(Role.ADMINISTRADOR); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    EventClaim claim(long id, long memberId, String name) {
        var member = new Member(); member.setId(memberId); member.setNickname(name);
        var objective = new Objective(); objective.setId(5L); objective.setName("PBs Major");
        var event = new GuildEvent(); event.setId(9L);
        var claim = new EventClaim(); claim.setId(id); claim.setMember(member); claim.setObjective(objective);
        claim.setEvent(event); claim.setPoints(10L); return claim;
    }
    @Test void uniqueRecipientsSortedWithAllClaims() {
        when(claims.findByIdIn(List.of(1L,2L,3L))).thenReturn(List.of(claim(1,8,"Zulu"),claim(2,7,"Alpha"),claim(3,7,"Alpha")));
        var result = service.rewardRecipients(List.of(1L,2L,3L));
        assertEquals("PBs Major",result.name()); assertEquals(2,result.recipients().size());
        assertEquals("Alpha",result.recipients().getFirst().nickname());
        assertEquals(List.of(2L,3L),result.recipients().getFirst().claimIds());
    }
    @Test void rejectsDeniedMissingNonpositiveAndMixedRounds() {
        var a=claim(1,7,"Alpha"); var b=claim(2,8,"Beta");
        when(claims.findByIdIn(List.of(1L,2L))).thenReturn(List.of(a,b));
        b.setDenied(true); assertThrows(AppException.class,()->service.rewardRecipients(List.of(1L,2L)));
        b.setDenied(false);b.setPoints(0L);assertThrows(AppException.class,()->service.rewardRecipients(List.of(1L,2L)));
        b.setPoints(10L);b.getEvent().setId(99L);assertThrows(AppException.class,()->service.rewardRecipients(List.of(1L,2L)));
        a.setEvent(null); b.setEvent(null); a.setClaimedAt(java.time.Instant.now().minusSeconds(86400));
        assertThrows(AppException.class,()->service.rewardRecipients(List.of(1L,2L)));
        when(claims.findByIdIn(List.of(1L,2L))).thenReturn(List.of(a));
        assertThrows(AppException.class,()->service.rewardRecipients(List.of(1L,2L)));
        assertThrows(AppException.class,()->service.rewardRecipients(List.of(1L,1L)));
    }
    @Test void supportsAutomatedPbClaimsWithoutGuildEvent() {
        var a=claim(1,7,"Alpha"); var b=claim(2,8,"Beta"); a.setEvent(null); b.setEvent(null);
        when(claims.findByIdIn(List.of(1L,2L))).thenReturn(List.of(a,b));
        assertEquals(2,service.rewardRecipients(List.of(1L,2L)).recipients().size());
    }
    @Test void nonAdminCannotReadRecipients() {
        authenticate(Role.MEMBRO);
        assertThrows(AppException.class,()->service.rewardRecipients(List.of(1L)));
        verifyNoInteractions(claims);
    }
}
