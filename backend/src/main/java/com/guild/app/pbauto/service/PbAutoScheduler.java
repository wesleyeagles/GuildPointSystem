package com.guild.app.pbauto.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Shouts, Pontos de RG e objetivo dos PBs diários automáticos. */
@Component
@ConditionalOnProperty(name = "guild.pb.scheduler", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class PbAutoScheduler {
    private final PbAutoService service;

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void tick() { service.tick(); }
}
