package com.guild.app.party.dto;

import com.guild.app.common.enums.PartyMap;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AssemblePartyRequest(
        @NotNull PartyMap map,
        @NotNull Long leaderMemberId,
        @NotEmpty @Size(max = 8) List<Long> memberIds) {}
