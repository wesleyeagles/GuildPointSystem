package com.guild.app.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code currentPassword} may be empty only when the account has no password yet. */
public record ChangePasswordRequest(
        String currentPassword,
        @NotBlank(message = "Informe a nova senha.")
        @Size(min = 6, max = 100, message = "A senha deve ter entre 6 e 100 caracteres.")
        String newPassword
) {}
