package com.guild.app.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DiscordProfileRequest(
        @NotBlank @Size(max = 100) String nickname,
        @NotNull Long raceId,
        @NotNull Long classId
) {}
