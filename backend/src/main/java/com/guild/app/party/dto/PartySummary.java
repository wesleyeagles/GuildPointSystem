package com.guild.app.party.dto;

import com.guild.app.common.enums.PartyMap;

import java.util.List;

public record PartySummary(
        Long id,
        int number,
        PartyMap map,
        String spot,
        Long leaderId,
        List<PartyMemberSummary> members,
        int memberCount
) {}
