package com.guild.app.party.service;

import com.guild.app.common.exception.AppException;
import com.guild.app.log.service.AuditLogService;
import com.guild.app.party.repository.PartyQueueAccessRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PartyQueueAccessServiceTest {
    final PartyQueueAccessRepository repository=mock(PartyQueueAccessRepository.class);
    final PartyQueueAccessService service=new PartyQueueAccessService(repository,mock(PartyBoardBroadcaster.class),mock(AuditLogService.class));
    void at(String instant) { service.clock=Clock.fixed(Instant.parse(instant),ZoneOffset.UTC); }

    @ParameterizedTest
    @CsvSource({
        "2026-10-31T03:00:00Z,true,2026-10-31T09:00:00Z",
        "2026-10-31T08:59:59Z,true,2026-10-31T09:00:00Z",
        "2026-10-31T09:00:00Z,false,2026-10-31T21:00:00Z",
        "2026-10-31T15:00:00Z,false,2026-10-31T21:00:00Z",
        "2026-10-31T20:59:59Z,false,2026-10-31T21:00:00Z",
        "2026-10-31T21:00:00Z,true,2026-11-01T09:00:00Z",
        "2026-11-01T02:59:59Z,true,2026-11-01T09:00:00Z"
    })
    void usesBrasiliaAndExactBoundaries(String now,boolean open,String boundary) {
        at(now); when(repository.read(false)).thenReturn(new PartyQueueAccessRepository.Override(null,null));
        var access=service.current();
        assertEquals(open,access.open());assertFalse(access.manual());assertEquals(Instant.parse(boundary),access.nextChangeAt());
    }
    @Test void manualOpeningExpiresExactlyAtNextBoundaryAndClosureLocksNewEntries() {
        at("2026-10-31T15:00:00Z");
        var until=Instant.parse("2026-10-31T21:00:00Z");
        when(repository.read(false)).thenReturn(new PartyQueueAccessRepository.Override(true,until));
        assertTrue(service.current().open());assertTrue(service.current().manual());
        at(until.toString());assertTrue(service.current().open());assertFalse(service.current().manual());
        when(repository.read(true)).thenReturn(new PartyQueueAccessRepository.Override(false,Instant.parse("2026-11-01T09:00:00Z")));
        assertThrows(AppException.class,service::requireOpen);
        verify(repository).read(true);
    }
}
