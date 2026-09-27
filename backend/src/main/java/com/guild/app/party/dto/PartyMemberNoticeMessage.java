package com.guild.app.party.dto;

import com.guild.app.common.enums.PartyMap;

public record PartyMemberNoticeMessage(
        String type,
        String message,
        Long partyId,
        PartyMap partyMap) {}
