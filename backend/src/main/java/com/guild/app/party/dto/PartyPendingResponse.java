package com.guild.app.party.dto;

import com.guild.app.common.enums.PartyMap;
import com.guild.app.common.enums.PartyPendingKind;

import java.time.Instant;

public record PartyPendingResponse(
        Long id,
        PartyPendingKind kind,
        Long partyId,
        PartyMap partyMap,
        PartyMemberSummary otherMember,
        Instant createdAt
) {}
