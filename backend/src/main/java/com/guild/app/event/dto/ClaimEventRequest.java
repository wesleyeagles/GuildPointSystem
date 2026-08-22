package com.guild.app.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ClaimEventRequest(
        @NotBlank(message = "Informe a senha do evento.")
        @Pattern(regexp = "^.{4}$", message = "A senha deve ter exatamente 4 caracteres.")
        String password
) {}
