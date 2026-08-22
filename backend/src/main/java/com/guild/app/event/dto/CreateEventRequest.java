package com.guild.app.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateEventRequest(
        @NotNull(message = "Selecione um objetivo.")
        Long objectiveId,
        @NotNull(message = "Selecione a duração do evento.")
        Integer durationMinutes,
        @NotBlank(message = "Informe a senha do evento.")
        @Pattern(regexp = "^.{4}$", message = "A senha deve ter exatamente 4 caracteres.")
        String password
) {}
