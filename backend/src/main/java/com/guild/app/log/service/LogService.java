package com.guild.app.log.service;

import com.guild.app.log.dto.AuditLogResponse;
import com.guild.app.log.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> list(Pageable pageable) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(log -> new AuditLogResponse(
                        log.getId(),
                        log.getType(),
                        log.getActor() != null ? log.getActor().getId() : null,
                        log.getActor() != null ? log.getActor().getNickname() : "System",
                        log.getTarget() != null ? log.getTarget().getId() : null,
                        log.getTarget() != null ? log.getTarget().getNickname() : null,
                        log.getPayload(),
                        log.getCreatedAt()));
    }
}
