package com.guild.app.party.dto;

import java.time.Instant;

public record PartyLfgEntry(
        Long memberId,
        String nickname,
        String className,
        String classImageUrl,
        int level,
        String note,
        Instant createdAt
) {}
