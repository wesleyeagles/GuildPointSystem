package com.guild.app.log.dto;

import com.guild.app.common.enums.AuditLogType;

/**
 * Filtros da aba Logs; todos opcionais.
 * {@code memberId} casa com quem fez (actor) ou com quem recebeu (target).
 * {@code objective} e o nome do evento/objetivo gravado no payload.
 */
public record LogFilter(AuditLogType type, Long memberId, String objective) {
}
