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
 * Pontos automáticos dos PBs diários. Para cada PB da lista {@link #RUNS}, todo dia (horário de Brasília):
 * <ul>
 *   <li>shouts do "Sistema" no jogo nos minutos marcados antes do RG;</li>
 *   <li>no horário do RG, Pontos de RG para quem está na base (local da DLL com até 3 min, AFK ou não);</li>
 *   <li>do RG até o fim da janela, cada envio da DLL nas regiões do PB marca presença; no fim da janela quem
 *       passou por lá recebe o objetivo do PB (os pontos entram sozinhos, como na CW).</li>
 * </ul>
 * Cada passo é idempotente (tabelas pb_auto_*): o tick a cada 30 s e reinícios do backend não pontuam duas vezes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PbAutoService {

    /**
     * Um PB diário automático. {@code shoutsBefore} = minutos antes do RG; presença vale de {@code rgAt} até
     * {@code endAt}. Mapas e regiões são os nomes que a DLL envia (sem diferença de maiúscula).
     */
    public record Run(String code, String label, LocalTime rgAt, LocalTime endAt, List<Integer> shoutsBefore,
                      String shout, String rgMap, Set<String> rgRegions, long rgPoints,
                      String pbMap, Set<String> pbRegions, String objective) {}

    public static final List<Run> RUNS = List.of(
            new Run("COLONIA_02", "PB Colônia 02:00", LocalTime.of(1, 52), LocalTime.of(2, 5), List.of(12, 7, 2),
                    "RG Base Avançada, Pontos de RG as 01:52",
                    "Cora HQ", Set.of("Cora Outpost"), 2,
                    "Haram", Set.of("Shadow Wood", "Rock Cavern"), "PB Colônia"),
            new Run("HQ_22", "PB HQ 22:00", LocalTime.of(21, 52), LocalTime.of(22, 5), List.of(12, 7, 2),
                    "RG Base Avançada, Pontos de RG as 21:52",
                    "Cora HQ", Set.of("Cora Outpost"), 2,
                    "Cora HQ", Set.of("Dark Plain", "Hunter Cave"), "PB HQ"));

    static final ZoneId BRT = GameEventDefinitions.SCHEDULE_ZONE;
    /** Local mais velho que isso não conta para o RG (a DLL envia a cada ~1 min). */
    static final Duration FRESH_LOCATION = Duration.ofMinutes(3);
    /** Até quanto tempo depois do horário o tick ainda dá o RG / o objetivo (backend fora do ar no minuto exato). */
    static final Duration RG_GRACE = Duration.ofMinutes(3);
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
    /** Shouts já dados ("data|código|minutos"). */
    private final Set<String> shouted = ConcurrentHashMap.newKeySet();
    private Clock clock = Clock.systemUTC();

    /** Testes: relógio fixo. */
    public void useClock(Clock value) { clock = value; }

    static Instant at(LocalDate day, LocalTime time) { return day.atTime(time).atZone(BRT).toInstant(); }

    // ---- Presença (cada envio de status da DLL) ----

    /** Envio de status: dentro da janela de um PB e numa das regiões dele, marca presença. */
    @Transactional
    public void record(long memberId, String character, String map, String region, Instant now) {
        if (map == null || region == null) return;
        LocalDate day = now.atZone(BRT).toLocalDate();
        for (Run run : RUNS) {
            if (now.isBefore(at(day, run.rgAt())) || now.isAfter(at(day, run.endAt()))) continue;
            if (!same(map, run.pbMap()) || run.pbRegions().stream().noneMatch(r -> same(region, r))) continue;
            jdbc.update("""
                    INSERT INTO pb_auto_presence(run_date,code,member_id,first_seen_at,last_seen_at,character_name,region_name)
                    VALUES(?,?,?,?,?,?,?)
                    ON CONFLICT (run_date,code,member_id) DO UPDATE SET last_seen_at=EXCLUDED.last_seen_at""",
                    Date.valueOf(day), run.code(), memberId, Timestamp.from(now), Timestamp.from(now),
                    character, region.strip());
        }
    }

    // ---- Agendador ----

    /** Chamado a cada 30 s (PbAutoScheduler). */
    @Transactional
    public void tick() {
        Instant now = clock.instant();
        LocalDate today = now.atZone(BRT).toLocalDate();
        for (LocalDate day : List.of(today.minusDays(1), today)) {
            for (Run run : RUNS) {
                Instant rgAt = at(day, run.rgAt());
                for (int minutes : run.shoutsBefore()) {
                    Instant shoutAt = rgAt.minus(Duration.ofMinutes(minutes));
                    if (!now.isBefore(shoutAt) && now.isBefore(shoutAt.plus(Duration.ofMinutes(1)))
                            && shouted.add(day + "|" + run.code() + "|" + minutes)) {
                        shoutService.system(run.shout());
                    }
                }
                if (!now.isBefore(rgAt) && now.isBefore(rgAt.plus(RG_GRACE))) rg(day, run, now);
                Instant endAt = at(day, run.endAt());
                if (!now.isBefore(endAt) && now.isBefore(endAt.plus(AWARD_GRACE))) award(day, run, now);
            }
        }
        shouted.removeIf(k -> k.compareTo(today.minusDays(2).toString()) < 0);
    }

    /** Pontos de RG para quem está na base agora (AFK ou não). */
    private void rg(LocalDate day, Run run, Instant now) {
        Date date = Date.valueOf(day);
        jdbc.update("INSERT INTO pb_auto_run(run_date,code) VALUES(?,?) ON CONFLICT DO NOTHING", date, run.code());
        if (jdbc.update("UPDATE pb_auto_run SET rg_at=? WHERE run_date=? AND code=? AND rg_at IS NULL",
                Timestamp.from(now), date, run.code()) == 0) return;
        var here = new TreeSet<Long>();
        for (CharacterStatus s : statusRepository.findByLocationAtAfter(now.minus(FRESH_LOCATION))) {
            if (same(s.getMapName(), run.rgMap()) && run.rgRegions().stream().anyMatch(r -> same(s.getRegionName(), r)))
                here.add(s.getMember().getId());
        }
        String place = String.join(", ", run.rgRegions()) + " · " + run.rgMap();
        String reason = "Pontos de RG: " + place + " (" + run.label() + " " + brtDay(day) + ")";
        for (Long id : here) {
            Member member = members.findByIdForUpdate(id).orElse(null);
            if (member == null || !eligible(member)) continue;
            if (jdbc.update("INSERT INTO pb_auto_award(run_date,code,member_id,kind,points) VALUES(?,?,?,'RG',?) ON CONFLICT DO NOTHING",
                    date, run.code(), id, run.rgPoints()) == 0) continue;
            pointsService.adjustPoints(member, member, run.rgPoints(), PointsModality.AJUSTE, reason);
            audit.log(AuditLogType.POINTS_ADJUSTED, member, member, Map.of(
                    "amount", run.rgPoints(),
                    "modality", PointsModality.AJUSTE.name(),
                    "reason", reason,
                    // Mesmo filtro "Evento" dos logs que o "Pontos de RG" manual.
                    "objectiveName", LocationPointsService.RG_OBJECTIVE,
                    "source", "RG"));
            publishPoints(id);
        }
    }

    /** Objetivo do PB para quem passou pelas regiões do PB na janela. Sem o objetivo cadastrado, tenta de novo no próximo tick. */
    private void award(LocalDate day, Run run, Instant now) {
        Date date = Date.valueOf(day);
        jdbc.update("INSERT INTO pb_auto_run(run_date,code) VALUES(?,?) ON CONFLICT DO NOTHING", date, run.code());
        boolean pending = !jdbc.queryForList("SELECT 1 FROM pb_auto_run WHERE run_date=? AND code=? AND awarded_at IS NULL FOR UPDATE",
                date, run.code()).isEmpty();
        if (!pending) return;
        Objective objective = objective(run.objective()).orElse(null);
        if (objective == null) {
            log.warn("PB automático {}: objetivo \"{}\" não existe; ninguém pontuou ainda", run.code(), run.objective());
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

    private Optional<Objective> objective(String name) {
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

    private static boolean same(String a, String b) { return a != null && b != null && a.strip().equalsIgnoreCase(b.strip()); }

    /** Sem acento e sem maiúscula: "PB Colonia" acha "PB Colônia". */
    private static String key(String s) {
        return Normalizer.normalize(Objects.toString(s, "").strip(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private static String brtDay(LocalDate day) { return String.format("%02d/%02d", day.getDayOfMonth(), day.getMonthValue()); }
}
