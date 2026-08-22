package com.guild.app.auction.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PlaceBidRequest(
        @NotNull(message = "Informe o valor do lance.")
        @Min(value = 1, message = "O lance deve ser de no mínimo 1 ponto.")
        Long amount
) {}
