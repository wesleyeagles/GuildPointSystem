package com.guild.app.party.service;

import com.guild.app.common.enums.AuditLogType;
import com.guild.app.common.exception.AppException;
import com.guild.app.log.service.AuditLogService;
import com.guild.app.member.entity.Member;
import com.guild.app.party.dto.PartyQueueAccess;
import com.guild.app.party.repository.PartyQueueAccessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PartyQueueAccessService {
    private static final ZoneId BRT=ZoneId.of("America/Sao_Paulo");
    private final PartyQueueAccessRepository repository;
    private final PartyBoardBroadcaster broadcaster;
    private final AuditLogService audit;
    Clock clock=Clock.systemUTC();

    static Instant nextBoundary(Instant now) {
        var local=now.atZone(BRT);
        var date=local.toLocalDate();
        if(local.getHour()<6) return date.atTime(6,0).atZone(BRT).toInstant();
        if(local.getHour()<18) return date.atTime(18,0).atZone(BRT).toInstant();
        return date.plusDays(1).atTime(6,0).atZone(BRT).toInstant();
    }
    private PartyQueueAccess access(boolean lock) {
        var override=repository.read(lock);
        var now=clock.instant();
        boolean manual=override.open()!=null && override.until().isAfter(now);
        int hour=now.atZone(BRT).getHour();
        return new PartyQueueAccess(manual?override.open():hour>=18||hour<6,manual,nextBoundary(now));
    }
    @Transactional(readOnly=true)
    public PartyQueueAccess current() { return access(false); }

    @Transactional
    public void requireOpen() {
        if(!access(true).open()) throw new AppException("Fila fechada. As entradas são permitidas das 18h às 06h (Brasília), ou quando um administrador abrir a fila.",HttpStatus.CONFLICT);
    }
    @Transactional
    public void setOpen(boolean open,Member actor) {
        var until=nextBoundary(clock.instant());
        repository.update(open,until);
        audit.log(AuditLogType.PARTY_QUEUE_UPDATED,actor,null,
            Map.of("action",open?"QUEUE_OPENED":"QUEUE_CLOSED","until",until.toString()));
        broadcaster.boardChanged();
    }
    @Scheduled(cron="0 0 6,18 * * *",zone="America/Sao_Paulo")
    public void boundaryChanged() { broadcaster.boardChanged(); }
}
