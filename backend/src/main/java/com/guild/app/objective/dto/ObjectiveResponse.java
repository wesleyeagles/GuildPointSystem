package com.guild.app.objective.dto;

import com.guild.app.common.enums.ObjectiveType;

import java.time.Instant;

public record ObjectiveResponse(
        Long id,
        String name,
        Long points,
        ObjectiveType type,
        Integer dailyLimit,
        Instant createdAt
) {}
