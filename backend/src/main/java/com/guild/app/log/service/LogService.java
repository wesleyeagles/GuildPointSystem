package com.guild.app.log.service;

import com.guild.app.common.enums.AuditLogType;
import com.guild.app.event.repository.EventClaimRepository;
import com.guild.app.log.dto.AuditLogResponse;
import com.guild.app.log.entity.AuditLog;
import com.guild.app.log.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guild.app.common.enums.Role;
import com.guild.app.common.security.SecurityUtils;
import com.guild.app.log.dto.LogFilter;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LogService {

    private final AuditLogRepository auditLogRepository;
    private final EventClaimRepository eventClaimRepository;

    /** Tipos so de admin: membros nao veem nem filtrando por eles. */
    private static final List<AuditLogType> ADMIN_ONLY = List.of(
            AuditLogType.EQUIPMENT_CHANGED, AuditLogType.EQUIPMENT_CATALOG_IMPORTED);

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> list(Pageable pageable) {
        return list(new LogFilter(null, null, null), pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> list(LogFilter filter, Pageable pageable) {
        boolean admin = SecurityUtils.currentMember().getRole().isAtLeast(Role.ADMINISTRADOR);
        var sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLog> page = auditLogRepository.findAll(spec(filter, admin), sorted);

        var claimIds = new HashSet<Long>();
        for (var log : page.getContent()) {
            if (log.getType() != AuditLogType.EVENT_CLAIMED) continue;
            Long claimId = parseClaimId(log.getPayload().get("claimId"));
            if (claimId != null) claimIds.add(claimId);
        }

        Map<Long, Boolean> deniedByClaimId = claimIds.isEmpty()
                ? Map.of()
                : eventClaimRepository.findAllById(claimIds).stream()
                        .collect(Collectors.toMap(c -> c.getId(), c -> c.isDenied()));

        return page.map(log -> toResponse(log, deniedByClaimId));
    }

    @Transactional(readOnly = true)
    public List<String> objectiveNames() {
        return auditLogRepository.findObjectiveNames();
    }

    private static Specification<AuditLog> spec(LogFilter filter, boolean admin) {
        String objective = filter.objective() == null || filter.objective().isBlank() ? null : filter.objective().trim();
        return (root, query, cb) -> {
            var where = new ArrayList<Predicate>();
            if (!admin) where.add(cb.not(root.get("type").in(ADMIN_ONLY)));
            // Equipamento sincronizado pelo jogo continua gravado, mas nunca aparece na aba Logs.
            var action = cb.function("jsonb_extract_path_text", String.class, root.get("payload"), cb.literal("action"));
            where.add(cb.or(
                    cb.notEqual(root.get("type"), AuditLogType.EQUIPMENT_CHANGED),
                    cb.isNull(action),
                    cb.notEqual(action, "CLIENT_SYNC")));
            if (filter.type() != null) where.add(cb.equal(root.get("type"), filter.type()));
            if (filter.memberId() != null) {
                where.add(cb.or(
                        cb.equal(root.get("actor").get("id"), filter.memberId()),
                        cb.equal(root.get("target").get("id"), filter.memberId())));
            }
            if (objective != null) {
                where.add(cb.equal(
                        cb.function("jsonb_extract_path_text", String.class, root.get("payload"), cb.literal("objectiveName")),
                        objective));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private Long parseClaimId(Object claimIdObj) {
        if (claimIdObj instanceof Number number) {
            return number.longValue();
        }
        if (claimIdObj instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private AuditLogResponse toResponse(AuditLog log, Map<Long, Boolean> deniedByClaimId) {
        var payload = new HashMap<>(log.getPayload());
        if (log.getType() == AuditLogType.EVENT_CLAIMED) {
            Long claimId = parseClaimId(payload.get("claimId"));
            if (claimId != null) {
                payload.put("claimDenied", deniedByClaimId.getOrDefault(claimId, false));
            }
        }

        return new AuditLogResponse(
                log.getId(),
                log.getType(),
                log.getActor() != null ? log.getActor().getId() : null,
                log.getActor() != null ? log.getActor().getNickname() : "System",
                log.getTarget() != null ? log.getTarget().getId() : null,
                log.getTarget() != null ? log.getTarget().getNickname() : null,
                payload,
                log.getCreatedAt());
    }
}
