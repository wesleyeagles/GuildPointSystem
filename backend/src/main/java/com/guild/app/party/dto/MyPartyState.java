package com.guild.app.party.dto;

import com.guild.app.common.enums.PartyMap;

import java.util.List;

public record MyPartyState(
        Long partyId,
        PartyMap partyMap,
        boolean leader,
        PartyLfgEntry lfg,
        List<PartyPendingResponse> pending
) {}
