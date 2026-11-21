package com.guild.app.auction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelAuctionBidRequest(@NotBlank @Size(max = 300) String reason) {}
