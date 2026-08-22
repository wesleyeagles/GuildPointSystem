package com.guild.app.objective.dto;

import com.guild.app.common.enums.ObjectiveType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ObjectiveRequest(
        @NotBlank(message = "Informe o nome do objetivo.")
        String name,
        @NotNull(message = "Informe os pontos.")
        @Min(value = 1, message = "Os pontos devem ser no mínimo 1.")
        Long points,
        @NotNull(message = "Selecione o tipo do objetivo.")
        ObjectiveType type,
        Integer dailyLimit
) {}
