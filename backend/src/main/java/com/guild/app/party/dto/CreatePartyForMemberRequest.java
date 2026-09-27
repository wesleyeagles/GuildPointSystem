package com.guild.app.party.dto;

import com.guild.app.common.enums.PartyMap;
import jakarta.validation.constraints.NotNull;

public record CreatePartyForMemberRequest(
        @NotNull PartyMap map,
        @NotNull Long leaderMemberId) {}
