package com.guild.app.pbauto.service;

import com.guild.app.common.enums.AuditLogType;
import com.guild.app.common.enums.Role;
import com.guild.app.common.exception.AppException;
import com.guild.app.common.security.SecurityUtils;
import com.guild.app.location.repository.GameRegionRepository;
import com.guild.app.log.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/** Admin → Pontos automáticos: criar, editar, ligar/desligar e apagar regras (Administrador e Líder). */
@Service
@RequiredArgsConstructor
public class AutoPointRuleService {

    /** Dados do formulário. Horários "HH:mm" de Brasília; weekdays 1 = segunda … 7 = domingo. */
    public record RuleRequest(String label, boolean enabled, List<Integer> weekdays, List<Integer> shoutsBefore,
                              String shout, String shoutEn,
                              boolean rgEnabled, String rgAt, String rgMap, List<String> rgRegions, Long rgPoints,
                              boolean presenceEnabled, String presenceFrom, String presenceTo, String presenceMap,
                              List<String> presenceRegions, String objectiveName) {}

    /** Uma rodada recente: quantos receberam o RG e o objetivo. */
    public record RunSummary(LocalDate date, int rg, int objective) {}

    public record RuleResponse(long id, String code, String label, boolean enabled, List<Integer> weekdays,
                               List<Integer> shoutsBefore, String shout, String shoutEn,
                               boolean rgEnabled, String rgAt, String rgMap, List<String> rgRegions, long rgPoints,
                               boolean presenceEnabled, String presenceFrom, String presenceTo, String presenceMap,
                               List<String> presenceRegions, String objectiveName,
                               /** O objetivo da presença não existe na aba Objetivos: ninguém recebe. */
                               boolean objectiveMissing,
                               List<RunSummary> lastRuns) {}

    /** Um mapa com as regiões já vistas pela DLL (para escolher no formulário). */
    public record MapOption(String map, List<String> regions) {}

    static final int MAX_SHOUTS = 5, MAX_SHOUT_MINUTES = 120, MAX_RG_POINTS = 100, MAX_REGIONS = 20;

    private final AutoPointRuleRepository repository;
    private final PbAutoService engine;
    private final GameRegionRepository regions;
    private final AuditLogService audit;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<RuleResponse> list() {
        SecurityUtils.requireRole(Role.ADMINISTRADOR);
        var runs = new HashMap<String, TreeMap<LocalDate, int[]>>();
        jdbc.query("""
                SELECT code, run_date, kind, count(*) FROM pb_auto_award
                WHERE run_date >= ? GROUP BY code, run_date, kind""", rs -> {
            int[] counts = runs.computeIfAbsent(rs.getString(1), k -> new TreeMap<>(Comparator.reverseOrder()))
                    .computeIfAbsent(rs.getDate(2).toLocalDate(), k -> new int[2]);
            counts["RG".equals(rs.getString(3)) ? 0 : 1] = rs.getInt(4);
        }, Date.valueOf(LocalDate.now(PbAutoService.BRT).minusDays(7)));
        return repository.findAll().stream().map(r -> toResponse(r,
                runs.getOrDefault(r.code(), new TreeMap<>()).entrySet().stream().limit(5)
                        .map(e -> new RunSummary(e.getKey(), e.getValue()[0], e.getValue()[1])).toList())).toList();
    }

    @Transactional(readOnly = true)
    public List<MapOption> maps() {
        SecurityUtils.requireRole(Role.ADMINISTRADOR);
        var byMap = new TreeMap<String, TreeSet<String>>(String.CASE_INSENSITIVE_ORDER);
        for (var r : regions.findAll()) byMap.computeIfAbsent(r.map(), k -> new TreeSet<>()).add(r.region());
        return byMap.entrySet().stream().map(e -> new MapOption(e.getKey(), List.copyOf(e.getValue()))).toList();
    }

    @Transactional
    public RuleResponse create(RuleRequest request) {
        SecurityUtils.requireRole(Role.ADMINISTRADOR);
        long id = repository.insert(parse(0, "", request));
        return saved(id, "CREATED");
    }

    @Transactional
    public RuleResponse update(long id, RuleRequest request) {
        SecurityUtils.requireRole(Role.ADMINISTRADOR);
        var current = repository.findById(id).orElseThrow(this::notFound);
        repository.update(id, parse(id, current.code(), request));
        return saved(id, "UPDATED");
    }

    @Transactional
    public void delete(long id) {
        SecurityUtils.requireRole(Role.ADMINISTRADOR);
        var current = repository.findById(id).orElseThrow(this::notFound);
        repository.delete(id);
        engine.reload();
        audit.log(AuditLogType.AUTO_POINT_RULE_UPDATED, SecurityUtils.currentMember(), null,
                Map.of("autoPointRule", current.label(), "action", "DELETED"));
    }

    private RuleResponse saved(long id, String action) {
        engine.reload();
        var rule = repository.findById(id).orElseThrow(this::notFound);
        audit.log(AuditLogType.AUTO_POINT_RULE_UPDATED, SecurityUtils.currentMember(), null,
                Map.of("autoPointRule", rule.label(), "action", action));
        return toResponse(rule, List.of());
    }

    private AppException notFound() { return new AppException("Regra não encontrada.", HttpStatus.NOT_FOUND); }

    private static AppException bad(String message) { return new AppException(message, HttpStatus.BAD_REQUEST); }

    /** Valida o formulário e monta a regra. */
    AutoPointRule parse(long id, String code, RuleRequest r) {
        String label = text(r.label());
        if (label.isEmpty() || label.length() > 80) throw bad("Dê um nome à regra (até 80 letras).");
        int weekdays = 0;
        for (Integer d : Optional.ofNullable(r.weekdays()).orElse(List.of())) {
            if (d == null || d < 1 || d > 7) throw bad("Dia da semana inválido.");
            weekdays |= 1 << (d - 1);
        }
        if (weekdays == 0) throw bad("Escolha pelo menos um dia da semana.");
        var shouts = new TreeSet<Integer>(Comparator.reverseOrder());
        for (Integer m : Optional.ofNullable(r.shoutsBefore()).orElse(List.of())) {
            if (m == null || m < 1 || m > MAX_SHOUT_MINUTES) throw bad("Shout: minutos antes entre 1 e " + MAX_SHOUT_MINUTES + ".");
            shouts.add(m);
        }
        if (shouts.size() > MAX_SHOUTS) throw bad("No máximo " + MAX_SHOUTS + " shouts.");
        String shout = text(r.shout()), shoutEn = text(r.shoutEn());
        if (!shouts.isEmpty() && shout.isEmpty()) throw bad("Escreva o texto do shout (ou tire os horários de shout).");
        if (shout.length() > 120 || shoutEn.length() > 120) throw bad("Shout com no máximo 120 letras.");

        AutoPointRule.Rg rg = null;
        if (r.rgEnabled()) {
            long points = Optional.ofNullable(r.rgPoints()).orElse(0L);
            if (points < 1 || points > MAX_RG_POINTS) throw bad("Pontos de RG entre 1 e " + MAX_RG_POINTS + ".");
            rg = new AutoPointRule.Rg(time(r.rgAt(), "RG"), map(r.rgMap(), "RG"), regions(r.rgRegions()), points);
        }
        AutoPointRule.Presence presence = null;
        if (r.presenceEnabled()) {
            String objective = text(r.objectiveName());
            if (objective.isEmpty()) throw bad("Escolha o objetivo que a presença dá.");
            LocalTime from = time(r.presenceFrom(), "início da presença"), to = time(r.presenceTo(), "fim da presença");
            if (from.equals(to)) throw bad("A presença precisa terminar depois de começar.");
            presence = new AutoPointRule.Presence(from, to, map(r.presenceMap(), "presença"), regions(r.presenceRegions()), objective);
        }
        if (rg == null && presence == null) throw bad("Ligue o RG, a presença ou os dois.");
        var rule = new AutoPointRule(id, code, label, r.enabled(), weekdays, List.copyOf(shouts),
                shout.isEmpty() ? null : shout, shoutEn.isEmpty() ? null : shoutEn, rg, presence);
        if (presence != null) {
            LocalDate any = LocalDate.of(2026, 1, 5);
            var hours = java.time.Duration.between(rule.presenceFrom(any), rule.presenceTo(any)).toMinutes();
            if (hours > 6 * 60) throw bad("A janela de presença pode ter no máximo 6 h.");
        }
        return rule;
    }

    private RuleResponse toResponse(AutoPointRule r, List<RunSummary> lastRuns) {
        var days = new ArrayList<Integer>();
        for (int d = 1; d <= 7; d++) if ((r.weekdays() & (1 << (d - 1))) != 0) days.add(d);
        var rg = r.rg();
        var p = r.presence();
        boolean missing = p != null && engine.objective(p.objective()).isEmpty();
        return new RuleResponse(r.id(), r.code(), r.label(), r.enabled(), days, r.shoutsBefore(), r.shout(), r.shoutEn(),
                rg != null, rg != null ? hhmm(rg.at()) : null, rg != null ? rg.map() : null,
                rg != null ? rg.regions() : List.of(), rg != null ? rg.points() : 0,
                p != null, p != null ? hhmm(p.from()) : null, p != null ? hhmm(p.to()) : null, p != null ? p.map() : null,
                p != null ? p.regions() : List.of(), p != null ? p.objective() : null, missing, lastRuns);
    }

    private static String hhmm(LocalTime t) { return String.format("%02d:%02d", t.getHour(), t.getMinute()); }

    private static String text(String s) { return Objects.toString(s, "").strip(); }

    private static LocalTime time(String value, String what) {
        try {
            return LocalTime.parse(text(value)).withSecond(0).withNano(0);
        } catch (DateTimeParseException e) {
            throw bad("Horário inválido (" + what + "): use HH:mm.");
        }
    }

    private static String map(String value, String what) {
        String map = text(value);
        if (map.isEmpty() || map.length() > 64) throw bad("Escolha o mapa (" + what + ").");
        return map;
    }

    private static List<String> regions(List<String> values) {
        var list = Optional.ofNullable(values).orElse(List.of()).stream().map(AutoPointRuleService::text)
                .filter(s -> !s.isEmpty()).distinct().toList();
        if (list.size() > MAX_REGIONS || list.stream().anyMatch(s -> s.length() > 64 || s.contains("\n")))
            throw bad("Regiões inválidas.");
        return list;
    }
}
