package com.guild.app.points.dto;

import com.guild.app.common.enums.PointsModality;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PointsAdjustmentRequest(
        @NotNull Long amount,
        @NotNull PointsModality modality,
        @NotBlank String reason
) {}
