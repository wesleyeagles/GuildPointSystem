package com.guild.app.party.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PartyNoticeRequest(
        @NotNull @Size(max = 1000, message = "O aviso deve ter no máximo 1000 caracteres.") String text
) {}
