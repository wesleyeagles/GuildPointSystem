package com.guild.app.party.dto;

import com.guild.app.common.enums.PartyMap;

import java.util.List;

public record PartyBoardResponse(
        PartyMap map,
        List<PartySummary> parties,
        List<PartyLfgEntry> lfg,
        MyPartyState my
) {}
