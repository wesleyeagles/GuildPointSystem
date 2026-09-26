package com.guild.app.party.dto;

import com.guild.app.common.enums.PartyMap;

import java.util.List;

public record PartySummary(
        Long id,
        PartyMap map,
        Long leaderId,
        List<PartyMemberSummary> members,
        int memberCount
) {}
