package com.guild.app.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String nickname,
        @NotNull Long raceId,
        @NotNull Long classId,
        String avatarUrl
) {}
