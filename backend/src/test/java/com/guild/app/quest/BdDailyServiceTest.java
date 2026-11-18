package com.guild.app.quest;

import com.guild.app.common.enums.MemberStatus;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.quest.dto.BdCompletionRequest;
import com.guild.app.quest.repository.BdCompletionRepository;
import com.guild.app.quest.service.BdDailyService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class BdDailyServiceTest {
    final MemberRepository members = mock(MemberRepository.class, withSettings().mockMaker(org.mockito.MockMakers.SUBCLASS));
    final BdCompletionRepository repository = mock(BdCompletionRepository.class, withSettings().mockMaker(org.mockito.MockMakers.SUBCLASS));
    final BdDailyService service = new BdDailyService(members, repository);
    BdDailyServiceTest() {
        Member meow = new Member(); meow.setNickname("meow"); meow.setStatus(MemberStatus.APROVADO); meow.setProfileComplete(true);
        when(members.findByGameCharacter("meow")).thenReturn(List.of(meow));
        when(members.findByStatusOrderByPointsDesc(MemberStatus.APROVADO)).thenReturn(List.of(meow));
    }
    BdCompletionRequest event(String code, int complete) {
        return new BdCompletionRequest("meow", code, Instant.parse("2026-11-17T02:59:00Z").getEpochSecond(), 1, complete, BdDailyService.HASH);
    }
    @Test void recordsTheObservationBeforeOkOnItsCivilDay() {
        var event = event("Q070613", 104);
        service.ingest(event);
        verify(repository).record(event, LocalDate.parse("2026-11-16"), Instant.parse("2026-11-17T02:59:00Z"));
    }
    @Test void rejectsPendingQuestAndRemovalAndUnknownHash() {
        assertThrows(ResponseStatusException.class, () -> service.ingest(event("Q394782", 104)));
        assertThrows(ResponseStatusException.class, () -> service.ingest(event("Q070613", 0)));
        var e = event("Q070613",104);
        assertThrows(ResponseStatusException.class, () -> service.ingest(new BdCompletionRequest("meow",e.code(),e.observedAt(),1,104,"unknown")));
        verifyNoInteractions(repository);
    }
    @Test void deniesUnapprovedAndFormatsNativeProtocol() {
        assertThrows(ResponseStatusException.class, () -> service.forGame("unknown"));
        when(repository.forDay(any())).thenReturn(Map.of("meow", Instant.parse("2026-11-17T13:30:00Z")));
        String body = service.forGame("meow");
        assertTrue(body.startsWith("BD1\t")); assertTrue(body.contains("meow\t10:30:00\n"));
    }
}
