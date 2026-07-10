package com.guild.app.objective.dto;

import com.guild.app.common.enums.ObjectiveType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ObjectiveRequest(
        @NotBlank String name,
        @NotNull @Min(1) Long points,
        @NotNull ObjectiveType type,
        Integer dailyLimit
) {}
