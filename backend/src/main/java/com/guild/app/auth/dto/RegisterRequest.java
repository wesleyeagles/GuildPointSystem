package com.guild.app.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Informe o email.")
        @Email(message = "Informe um email válido.")
        String email,
        @NotBlank(message = "Informe a senha.")
        @Size(min = 6, message = "A senha deve ter no mínimo 6 caracteres.")
        String password,
        @NotBlank(message = "Informe o nickname.")
        @Size(max = 100, message = "O nickname pode ter até 100 caracteres.")
        String nickname,
        @NotNull(message = "Selecione a raça.")
        Long raceId,
        @NotNull(message = "Selecione a classe.")
        Long classId,
        String avatarUrl
) {}
