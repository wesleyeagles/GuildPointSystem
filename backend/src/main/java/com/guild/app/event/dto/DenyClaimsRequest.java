package com.guild.app.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Cancela uma rodada inteira (ex.: evento lançado em dobro): tira os pontos de todos os resgates dela. */
public record DenyClaimsRequest(
        @NotEmpty(message = "Nenhum resgate informado.")
        @Size(max = 500, message = "Resgates demais de uma vez.")
        List<Long> claimIds,
        @NotBlank(message = "Informe o motivo da remoção.")
        String reason
) {}
