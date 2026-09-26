package com.guild.app.party.dto;

import com.guild.app.common.enums.PartyMap;

import java.time.Instant;

public record PartyLfgEntry(
        Long memberId,
        String nickname,
        String className,
        String classImageUrl,
        int level,
        PartyMap map,
        String note,
        Instant createdAt
) {}
