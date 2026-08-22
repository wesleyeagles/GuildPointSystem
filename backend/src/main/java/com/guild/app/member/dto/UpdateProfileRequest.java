package com.guild.app.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "Informe o nickname.")
        @Size(max = 100, message = "O nickname pode ter até 100 caracteres.")
        String nickname,
        @NotNull(message = "Selecione a raça.")
        Long raceId,
        @NotNull(message = "Selecione a classe.")
        Long classId,
        String avatarUrl
) {}
