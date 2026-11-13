package com.guild.app.locationhistory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Apaga o histórico de local com mais de 3 dias, de hora em hora. */
@Component
@ConditionalOnProperty(name = "guild.pb.scheduler", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class LocationHistoryScheduler {
    private final LocationHistoryService service;

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    public void cleanup() { service.cleanup(Instant.now()); }
}
