package com.guild.app.cw.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Bônus de 10 min antes, fechamento das CWs sem aviso e pontuação 2 min depois do fim. */
@Component
@ConditionalOnProperty(name = "guild.cw.scheduler", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class CwScheduler {
    private final CwAttendanceService service;

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void tick() { service.tick(); }
}
