package com.guild.app.auction.dto;

import com.guild.app.auction.entity.AuctionBid;
import java.time.Instant;

public record AuctionBidResponse(Long id, Long memberId, String nickname, long amount, Instant createdAt,
                                 Instant cancelledAt, String cancelledBy, String cancellationReason) {
    public static AuctionBidResponse from(AuctionBid bid) {
        return new AuctionBidResponse(bid.getId(), bid.getMember().getId(), bid.getMember().getNickname(),
                bid.getAmount(), bid.getCreatedAt(), bid.getCancelledAt(),
                bid.getCancelledBy() == null ? null : bid.getCancelledBy().getNickname(), bid.getCancellationReason());
    }
}
