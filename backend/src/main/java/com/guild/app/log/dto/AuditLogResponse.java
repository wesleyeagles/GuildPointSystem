package com.guild.app.log.dto;

import com.guild.app.common.enums.AuditLogType;

import java.time.Instant;
import java.util.Map;

public record AuditLogResponse(
        Long id,
        AuditLogType type,
        Long actorId,
        String actorNickname,
        Long targetId,
        String targetNickname,
        Map<String, Object> payload,
        Instant createdAt
) {}
