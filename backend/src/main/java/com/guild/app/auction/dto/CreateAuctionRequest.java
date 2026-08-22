package com.guild.app.auction.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateAuctionRequest(
        @NotNull(message = "Selecione a duração do leilão.")
        @Min(value = 1, message = "A duração deve ser de no mínimo 1 minuto.")
        Integer durationMinutes,
        @NotEmpty(message = "Adicione pelo menos um item ao leilão.")
        List<AuctionItemEntry> items
) {
    public record AuctionItemEntry(
            @NotNull(message = "Selecione um item.")
            Long itemId,
            @Min(value = 1, message = "A quantidade deve ser no mínimo 1.")
            int quantity
    ) {}
}
