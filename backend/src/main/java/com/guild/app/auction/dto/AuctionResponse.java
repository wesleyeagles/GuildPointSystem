package com.guild.app.auction.dto;

import com.guild.app.common.enums.AuctionStatus;

import java.time.Instant;
import java.util.List;

public record AuctionResponse(
        Long id,
        AuctionStatus status,
        Integer durationMinutes,
        Instant createdAt,
        Instant endsAt,
        Long currentBid,
        Long winnerId,
        String winnerNickname,
        Long leaderId,
        String leaderNickname,
        int tiedCount,
        boolean tied,
        List<Long> tiedMemberIds,
        List<String> tiedMemberNicknames,
        List<AuctionItemResponse> items,
        Long tieBreakSeed
) {
    public record AuctionItemResponse(Long itemId, String itemName, String imageUrl, int quantity) {}
}
