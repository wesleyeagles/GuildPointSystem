package com.guild.app.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateEventRequest(
        @NotNull Long objectiveId,
        @NotNull Integer durationMinutes,
        @NotBlank @Pattern(regexp = "^.{4}$") String password
) {}
