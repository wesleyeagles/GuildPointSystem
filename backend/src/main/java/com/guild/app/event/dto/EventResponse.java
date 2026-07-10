package com.guild.app.event.dto;

import java.time.Instant;

public record EventResponse(
        Long id,
        Long objectiveId,
        String objectiveName,
        Long points,
        Integer durationMinutes,
        Instant createdAt,
        Instant expiresAt,
        boolean active,
        boolean claimedByMe
) {}
