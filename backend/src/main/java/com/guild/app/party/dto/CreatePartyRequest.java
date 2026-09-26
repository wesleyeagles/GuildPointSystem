package com.guild.app.party.dto;

import com.guild.app.common.enums.PartyMap;
import jakarta.validation.constraints.NotNull;

public record CreatePartyRequest(@NotNull PartyMap map) {}
