package com.guild.app.pbauto.service;

import com.guild.app.characterstatus.entity.CharacterStatus;
import com.guild.app.characterstatus.repository.CharacterStatusRepository;
import com.guild.app.common.enums.AuditLogType;
import com.guild.app.common.enums.MemberStatus;
import com.guild.app.common.enums.ObjectiveType;
import com.guild.app.common.enums.PointsModality;
import com.guild.app.event.entity.EventClaim;
import com.guild.app.event.repository.EventClaimRepository;
import com.guild.app.location.service.LocationPointsService;
import com.guild.app.log.service.AuditLogService;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.objective.entity.Objective;
import com.guild.app.objective.repository.ObjectiveRepository;
import com.guild.app.points.service.PointsService;
import com.guild.app.schedule.GameEventDefinitions;
import com.guild.app.shout.service.ShoutService;
import com.guild.app.websocket.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pontos automáticos (Admin → Pontos automáticos, regras em {@link AutoPointRuleRepository}). Para cada regra ligada,
 * nos dias da semana dela (horário de Brasília):
 * <ul>
 *   <li>shouts do "Sistema" no jogo nos minutos marcados antes do início;</li>
 *   <li>RG (opcional): no horário, Pontos de RG para quem está no local (local da DLL com até 3 min, AFK ou não);</li>
 *   <li>presença (opcional): cada envio da DLL no local durante a janela marca presença; no fim da janela quem
 *       passou por lá recebe o objetivo (os pontos entram sozinhos, como na CW).</li>
 * </ul>
 * Cada passo é idempotente (tabelas pb_auto_*, pelo código da regra): o tick a cada 30 s e reinícios do backend não
 * pontuam duas vezes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PbAutoService {

    static final ZoneId BRT = GameEventDefinitions.SCHEDULE_ZONE;
    /** Local mais velho que isso não conta para o RG (a DLL envia a cada ~1 min). */
    static final Duration FRESH_LOCATION = Duration.ofMinutes(3);
    /** Até quanto tempo depois do horário o tick ainda dá o RG / o objetivo (backend fora do ar no minuto exato). */
    static final Duration RG_GRACE = Duration.ofMinutes(3);
    /** Quem chega no local até isso depois do horário do RG ainda recebe (local enviado até lá). */
    static final Duration RG_LATE = Duration.ofMinutes(1);
    static final Duration AWARD_GRACE = Duration.ofHours(1);

    private final JdbcTemplate jdbc;
    private final MemberRepository members;
    private final ObjectiveRepository objectives;
    private final EventClaimRepository claims;
    private final PointsService pointsService;
    private final AuditLogService audit;
    private final WebSocketPublisher webSocketPublisher;
    private final CharacterStatusRepository statusRepository;
    private final ShoutService shoutService;
    private final AutoPointRuleRepository rules;
    /** Regras em memória (cada envio da DLL passa por {@link #record}); recarregadas a cada tick e ao salvar. */
    private volatile List<AutoPointRule> cached;
    /** Shouts já dados ("data|código|minutos"). */
    private final Set<String> shouted = ConcurrentHashMap.newKeySet();
    private Clock clock = Clock.systemUTC();

    /** Testes: relógio fixo. */
    public void useClock(Clock value) { clock = value; }

    /** Recarrega as regras do banco (depois de criar/editar/apagar uma regra). */
    public void reload() { cached = rules.findAll(); }

    private List<AutoPointRule> active() {
        List<AutoPointRule> list = cached;
        if (list == null) { reload(); list = cached; }
        return list.stream().filter(r -> r.enabled() && (r.rg() != null || r.presence() != null)).toList();
    }

    // ---- Presença (cada envio de status da DLL) ----

    /** Envio de status: dentro da janela de presença de uma regra e no local dela, marca presença. */
    @Transactional
    public void record(long memberId, String character, String map, String region, Instant now) {
        if (map == null) return;
        LocalDate today = now.atZone(BRT).toLocalDate();
        for (AutoPointRule run : active()) {
            var p = run.presence();
            if (p == null || !AutoPointRule.matches(map, region, p.map(), p.regions())) continue;
            for (LocalDate day : List.of(today.minusDays(1), today)) {
                if (!run.runsOn(day)) continue;
                if (now.isBefore(AutoPointRule.at(run.presenceFrom(day)))
                        || now.isAfter(AutoPointRule.at(run.presenceTo(day)))) continue;
                jdbc.update("""
                        INSERT INTO pb_auto_presence(run_date,code,member_id,first_seen_at,last_seen_at,character_name,region_name)
                        VALUES(?,?,?,?,?,?,?)
                        ON CONFLICT (run_date,code,member_id) DO UPDATE SET last_seen_at=EXCLUDED.last_seen_at""",
                        Date.valueOf(day), run.code(), memberId, Timestamp.from(now), Timestamp.from(now),
                        character, region == null ? null : region.strip());
            }
        }
    }

    // ---- Agendador ----

    /** Chamado a cada 30 s (PbAutoScheduler). */
    @Transactional
    public void tick() {
        Instant now = clock.instant();
        LocalDate today = now.atZone(BRT).toLocalDate();
        reload();
        // Ontem também: presença que passa da meia-noite (+ 1 h de folga); amanhã: shout antes da meia-noite.
        for (LocalDate day : List.of(today.minusDays(1), today, today.plusDays(1))) {
            for (AutoPointRule run : active()) {
                if (!run.runsOn(day)) continue;
                shouts(day, run, now);
                if (run.rg() != null) {
                    Instant rgAt = run.rgAt(day);
                    if (!now.isBefore(rgAt) && now.isBefore(rgAt.plus(RG_GRACE))) rg(day, run, now);
                }
                if (run.presence() != null) {
                    Instant endAt = AutoPointRule.at(run.presenceTo(day));
                    if (!now.isBefore(endAt) && now.isBefore(endAt.plus(AWARD_GRACE))) award(day, run, now);
                }
            }
        }
        shouted.removeIf(k -> k.compareTo(today.minusDays(2).toString()) < 0);
    }

    private void shouts(LocalDate day, AutoPointRule run, Instant now) {
        String shout = Objects.toString(run.shout(), "").strip();
        if (shout.isEmpty()) return;
        String en = Objects.toString(run.shoutEn(), "").strip();
        Instant start = AutoPointRule.at(run.start(day));
        for (int minutes : run.shoutsBefore()) {
            Instant shoutAt = start.minus(Duration.ofMinutes(minutes));
            if (!now.isBefore(shoutAt) && now.isBefore(shoutAt.plus(Duration.ofMinutes(1)))
                    && shouted.add(day + "|" + run.code() + "|" + minutes)) {
                try {
                    shoutService.system(shout, en.isEmpty() ? shout : en);
                } catch (IllegalArgumentException e) {
                    log.warn("Pontos automáticos {}: shout inválido ({})", run.code(), e.getMessage());
                }
            }
        }
    }

    /**
     * Pontos de RG para quem está no local (AFK ou não). Roda em todo tick do {@link #RG_GRACE}: quem chega até
     * {@link #RG_LATE} depois do horário (local enviado até lá) também recebe, uma vez só.
     */
    private void rg(LocalDate day, AutoPointRule run, Instant now) {
        var step = run.rg();
        Date date = Date.valueOf(day);
        jdbc.update("INSERT INTO pb_auto_run(run_date,code) VALUES(?,?) ON CONFLICT DO NOTHING", date, run.code());
        boolean first = jdbc.update("UPDATE pb_auto_run SET rg_at=? WHERE run_date=? AND code=? AND rg_at IS NULL",
                Timestamp.from(now), date, run.code()) > 0;
        Instant lateUntil = run.rgAt(day).plus(RG_LATE);
        var here = new TreeSet<Long>();
        for (CharacterStatus s : statusRepository.findByLocationAtAfter(now.minus(FRESH_LOCATION))) {
            // Depois do atraso só vale local enviado até o limite (o primeiro tick vale tudo: backend fora do ar na hora).
            if (!first && now.isAfter(lateUntil) && s.getLocationAt().isAfter(lateUntil)) continue;
            if (AutoPointRule.matches(s.getMapName(), s.getRegionName(), step.map(), step.regions()))
                here.add(s.getMember().getId());
        }
        String place = step.regions().isEmpty() ? step.map() : String.join(", ", step.regions()) + " · " + step.map();
        String reason = "Pontos de RG: " + place + " (" + run.label() + " " + brtDay(day) + ")";
        for (Long id : here) {
            Member member = members.findByIdForUpdate(id).orElse(null);
            if (member == null || !eligible(member)) continue;
            if (jdbc.update("INSERT INTO pb_auto_award(run_date,code,member_id,kind,points) VALUES(?,?,?,'RG',?) ON CONFLICT DO NOTHING",
                    date, run.code(), id, step.points()) == 0) continue;
            pointsService.adjustPoints(member, member, step.points(), PointsModality.AJUSTE, reason);
            audit.log(AuditLogType.POINTS_ADJUSTED, member, member, Map.of(
                    "amount", step.points(),
                    "modality", PointsModality.AJUSTE.name(),
                    "reason", reason,
                    // Mesmo filtro "Evento" dos logs que o "Pontos de RG" manual.
                    "objectiveName", LocationPointsService.RG_OBJECTIVE,
                    "source", "RG"));
            publishPoints(id);
        }
    }

    /** Objetivo para quem passou pelo local na janela. Sem o objetivo cadastrado, tenta de novo no próximo tick. */
    private void award(LocalDate day, AutoPointRule run, Instant now) {
        Date date = Date.valueOf(day);
        jdbc.update("INSERT INTO pb_auto_run(run_date,code) VALUES(?,?) ON CONFLICT DO NOTHING", date, run.code());
        boolean pending = !jdbc.queryForList("SELECT 1 FROM pb_auto_run WHERE run_date=? AND code=? AND awarded_at IS NULL FOR UPDATE",
                date, run.code()).isEmpty();
        if (!pending) return;
        Objective objective = objective(run.presence().objective()).orElse(null);
        if (objective == null) {
            log.warn("Pontos automáticos {}: objetivo \"{}\" não existe; ninguém pontuou ainda", run.code(), run.presence().objective());
            return;
        }
        jdbc.update("UPDATE pb_auto_run SET awarded_at=?, objective_id=? WHERE run_date=? AND code=?",
                Timestamp.from(now), objective.getId(), date, run.code());
        List<Long> present = jdbc.queryForList(
                "SELECT member_id FROM pb_auto_presence WHERE run_date=? AND code=? ORDER BY member_id", Long.class, date, run.code());
        for (Long id : present) {
            Member member = members.findByIdForUpdate(id).orElse(null);
            if (member == null || !eligible(member)) continue;
            if (objective.getType() == ObjectiveType.LIMITADO
                    && claims.countDailyClaims(id, objective.getId()) >= objective.getDailyLimit()) continue;
            if (jdbc.update("INSERT INTO pb_auto_award(run_date,code,member_id,kind,points) VALUES(?,?,?,'PB',?) ON CONFLICT DO NOTHING",
                    date, run.code(), id, objective.getPoints()) == 0) continue;
            pointsService.adjustPoints(member, member, objective.getPoints(), PointsModality.EVENTO, run.label() + " " + brtDay(day));
            var claim = new EventClaim();
            claim.setMember(member);
            claim.setObjective(objective);
            claim.setPoints(objective.getPoints());
            claim = claims.save(claim);
            jdbc.update("UPDATE pb_auto_award SET claim_id=? WHERE run_date=? AND code=? AND member_id=? AND kind='PB'",
                    claim.getId(), date, run.code(), id);
            var details = new LinkedHashMap<String, Object>();
            details.put("claimId", claim.getId());
            details.put("objectiveName", objective.getName());
            details.put("points", objective.getPoints());
            details.put("source", "PB");
            audit.log(AuditLogType.EVENT_CLAIMED, member, member, details);
            publishPoints(id);
        }
    }

    Optional<Objective> objective(String name) {
        String wanted = key(name);
        return objectives.findByDeletedFalseOrderByCreatedAtDesc().stream()
                .filter(o -> key(o.getName()).equals(wanted))
                .findFirst();
    }

    private void publishPoints(Long memberId) {
        webSocketPublisher.publishPointsUpdate(memberId, () -> Map.of(
                "memberId", memberId, "availablePoints", pointsService.getAvailablePoints(memberId)));
    }

    private static boolean eligible(Member m) {
        return m.getStatus() == MemberStatus.APROVADO && m.getDeletedAt() == null && m.getSuspendedAt() == null;
    }

    /** Sem acento e sem maiúscula: "PB Colonia" acha "PB Colônia". */
    static String key(String s) {
        return Normalizer.normalize(Objects.toString(s, "").strip(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private static String brtDay(LocalDate day) { return String.format("%02d/%02d", day.getDayOfMonth(), day.getMonthValue()); }
}
