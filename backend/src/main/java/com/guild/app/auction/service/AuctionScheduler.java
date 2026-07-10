package com.guild.app.auction.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuctionScheduler {

    private final AuctionService auctionService;

    @Scheduled(fixedRate = 1000)
    public void tick() {
        auctionService.processExpiredAuctions();
    }
}
