package com.guild.app.event.dto;

import jakarta.validation.constraints.NotBlank;

public record DenyClaimRequest(
        @NotBlank(message = "Informe o motivo da remoção.")
        String reason
) {}
