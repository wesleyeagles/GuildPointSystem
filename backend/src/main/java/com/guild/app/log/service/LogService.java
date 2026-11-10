package com.guild.app.log.service;

import com.guild.app.common.enums.AuditLogType;
import com.guild.app.event.repository.EventClaimRepository;
import com.guild.app.log.dto.AuditLogResponse;
import com.guild.app.log.dto.RecentPointsEntry;
import com.guild.app.log.entity.AuditLog;
import com.guild.app.log.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.guild.app.location.service.LocationPointsService;
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
    private final JdbcTemplate jdbc;

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

    /**
     * Últimas rodadas de pontos da guild (dashboard), sem jogador: os resgates de um evento viram uma
     * linha; sem evento (CW automática, RG, proximidade) junta o mesmo nome/local com até 10 min entre
     * um ponto e o próximo. Resgates removidos pelo admin não contam; ajuste manual de admin fica de fora.
     *
     * <p>Para {@code memberId}: se recebeu (e quanto) ou, se não recebeu, se foi por estar no limite
     * (objetivo LIMITADO já no limite diário naquele dia; ou {@link LocationPointsService#RG_PER_DAY} RG
     * no dia da CW).
     */
    @Transactional(readOnly = true)
    public List<RecentPointsEntry> recentPoints(int limit, Long memberId) {
        var params = new MapSqlParameterSource()
                .addValue("limit", Math.max(1, Math.min(limit, 50)))
                .addValue("me", memberId)
                .addValue("rgPerDay", LocationPointsService.RG_PER_DAY);
        return new NamedParameterJdbcTemplate(jdbc).query(RECENT_POINTS_SQL, params, (rs, i) -> new RecentPointsEntry(
                rs.getString("kind"),
                rs.getString("name"),
                rs.getString("detail"),
                rs.getInt("members"),
                rs.getBigDecimal("points"),
                rs.getBigDecimal("total"),
                rs.getTimestamp("last_at").toInstant(),
                rs.getBoolean("received"),
                rs.getBigDecimal("my_points"),
                rs.getBoolean("limited")));
    }

    private static final String RECENT_POINTS_SQL = """
            WITH src AS (
              SELECT a.id, a.created_at, a.target_id,
                     CASE WHEN a.type = 'EVENT_CLAIMED' THEN 'EVENT' ELSE a.payload->>'source' END AS kind,
                     CASE WHEN a.type = 'EVENT_CLAIMED' THEN a.payload->>'objectiveName'
                          WHEN a.payload->>'source' = 'RG' THEN 'RG'
                          -- proximidade grava quem estava perto no motivo: aqui é sem jogador
                          ELSE regexp_replace(a.payload->>'reason', '\\s*\\(perto de .*\\)$', '') END AS name,
                     CASE WHEN a.payload->>'source' = 'RG'
                          THEN regexp_replace(a.payload->>'reason', '^Pontos (de RG|por local):\\s*', '') END AS detail,
                     CASE WHEN a.type = 'EVENT_CLAIMED' THEN a.payload->>'eventId' END AS event_id,
                     COALESCE(a.payload->>'points', a.payload->>'amount')::numeric AS points,
                     c.objective_id
              FROM audit_log a
              LEFT JOIN event_claim c
                ON a.type = 'EVENT_CLAIMED' AND jsonb_typeof(a.payload->'claimId') = 'number'
               AND c.id = (a.payload->>'claimId')::bigint
              WHERE a.created_at > now() - interval '30 days'
                AND ((a.type = 'EVENT_CLAIMED' AND COALESCE(c.denied, false) = false)
                     OR (a.type = 'POINTS_ADJUSTED' AND a.payload->>'source' IN ('RG', 'CW', 'PROXIMITY')))
            ), keyed AS (
              SELECT *, COALESCE('e' || event_id, kind || '|' || COALESCE(name, '') || '|' || COALESCE(detail, '')) AS k
              FROM src
            ), marked AS (
              SELECT *, CASE
                  WHEN lag(created_at) OVER w IS NULL THEN 1
                  WHEN event_id IS NOT NULL THEN 0
                  WHEN created_at - lag(created_at) OVER w <= interval '10 minutes' THEN 0
                  ELSE 1 END AS starts
              FROM keyed
              WINDOW w AS (PARTITION BY k ORDER BY created_at, id)
            ), grouped AS (
              SELECT *, sum(starts) OVER (PARTITION BY k ORDER BY created_at, id) AS grp FROM marked
            ), rounds AS (
              SELECT min(kind) AS kind, min(name) AS name, min(detail) AS detail,
                     count(DISTINCT target_id) AS members, max(points) AS points, sum(points) AS total,
                     min(created_at) AS first_at, max(created_at) AS last_at,
                     COALESCE(bool_or(target_id = :me), false) AS received,
                     sum(points) FILTER (WHERE target_id = :me) AS my_points,
                     max(objective_id) AS objective_id
              FROM grouped
              GROUP BY k, grp
              ORDER BY last_at DESC
              LIMIT :limit
            )
            SELECT r.*,
                   CASE
                     WHEN r.received THEN false
                     -- evento LIMITADO: já tinha o limite do dia (event_claim.claimed_date é o dia UTC, como no resgate)
                     WHEN r.kind = 'EVENT' THEN EXISTS (
                       SELECT 1 FROM objective o
                       WHERE o.id = r.objective_id AND o.type = 'LIMITADO' AND o.daily_limit IS NOT NULL
                         AND (SELECT count(*) FROM event_claim m
                              WHERE m.member_id = :me AND m.objective_id = o.id AND NOT m.denied
                                AND m.claimed_date = (r.first_at AT TIME ZONE 'UTC')::date
                                AND m.claimed_at <= r.last_at) >= o.daily_limit)
                     -- RG perto da CW: já tinha o limite de RG do dia da CW (o dia vem do rg_award da rodada)
                     WHEN r.kind = 'RG' THEN (
                       SELECT count(*) FROM rg_award m
                       WHERE m.member_id = :me AND m.at < r.first_at
                         AND m.day = (SELECT ra.day FROM rg_award ra
                                      WHERE ra.at BETWEEN r.first_at - interval '5 seconds' AND r.last_at + interval '5 seconds'
                                      LIMIT 1)) >= :rgPerDay
                     ELSE false
                   END AS limited
            FROM rounds r
            ORDER BY r.last_at DESC
            """;

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
