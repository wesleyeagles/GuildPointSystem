package com.guild.app.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ClaimEventRequest(
        @NotBlank @Pattern(regexp = "^.{4}$") String password
) {}
