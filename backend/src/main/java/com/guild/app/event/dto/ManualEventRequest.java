package com.guild.app.event.dto;

import jakarta.validation.constraints.NotNull;

public record ManualEventRequest(
        @NotNull(message = "Selecione um objetivo.")
        Long objectiveId
) {}
