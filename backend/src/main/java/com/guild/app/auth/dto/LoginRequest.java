package com.guild.app.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Informe o email.")
        String email,
        @NotBlank(message = "Informe a senha.")
        String password
) {}
