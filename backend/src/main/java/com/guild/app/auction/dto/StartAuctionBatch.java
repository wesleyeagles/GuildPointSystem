package com.guild.app.auction.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.List;

public record StartAuctionBatch(
        @NotEmpty List<@NotNull @Positive Long> auctionIds,
        @NotNull Instant endsAt
) {}
