package com.guild.app.points.dto;

import com.guild.app.common.enums.PointsModality;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PointsAdjustmentRequest(
        @NotNull(message = "Informe a quantidade de pontos.")
        Long amount,
        @NotNull(message = "Selecione a modalidade.")
        PointsModality modality,
        @NotBlank(message = "Informe o motivo do ajuste.")
        String reason
) {}
