package com.guild.app.party.dto;

import com.guild.app.common.enums.PartyMap;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpsertLfgRequest(
        @NotNull PartyMap map,
        @Size(max = 200) String note
) {}
