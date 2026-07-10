package com.guild.app.auction.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateAuctionRequest(
        @NotNull @Min(1) Integer durationMinutes,
        @NotEmpty List<AuctionItemEntry> items
) {
    public record AuctionItemEntry(@NotNull Long itemId, @Min(1) int quantity) {}
}
