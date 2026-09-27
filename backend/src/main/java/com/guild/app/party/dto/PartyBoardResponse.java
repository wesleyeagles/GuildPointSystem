package com.guild.app.party.dto;

import java.util.List;

public record PartyBoardResponse(
        List<PartySummary> parties,
        List<PartyLfgEntry> lfg,
        MyPartyState my
) {}
