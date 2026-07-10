package com.guild.app.auction.dto;

import java.time.Instant;

public record AuctionMessageResponse(
        Long id,
        String type,
        String content,
        String imageUrl,
        Instant createdAt,
        MemberSummary member
) {
    public record MemberSummary(Long id, String nickname) {}
}
