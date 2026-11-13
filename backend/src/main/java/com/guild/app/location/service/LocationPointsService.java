package com.guild.app.location.service;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.characterstatus.entity.CharacterStatus;
import com.guild.app.characterstatus.repository.CharacterStatusRepository;
import com.guild.app.common.enums.AuditLogType;
import com.guild.app.common.enums.MemberStatus;
import com.guild.app.common.enums.PointsModality;
import com.guild.app.common.enums.Role;
import com.guild.app.common.exception.AppException;
import com.guild.app.common.security.SecurityUtils;
import com.guild.app.location.dto.LocationPointsDtos.*;
import com.guild.app.location.repository.GameRegionRepository;
import com.guild.app.log.service.AuditLogService;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.party.entity.PartyLfg;
import com.guild.app.party.repository.PartyLfgRepository;
import com.guild.app.party.service.PartyBoardBroadcaster;
import com.guild.app.points.service.PointsService;
import com.guild.app.websocket.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import com.guild.app.cw.service.CwAttendanceService;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pontos por local: o moderador escolhe mapa e/ou regiao, confere quem a DLL viu la
 * e confirma. O local vem do cliente (nao autenticado), por isso nunca e automatico:
 * sempre passa pela previa e pela confirmacao de alguem da staff.
 */
@Service
@RequiredArgsConstructor
public class LocationPointsService {

    /** A DLL envia o local a cada minuto: 3 min sem envio = saiu do jogo ou do local. */
    static final Duration FRESH = Duration.ofMinutes(3);
    /**
     * Pontos de RG (pontos gerais) dados nos {@link #RG_BEFORE_CW} antes de uma CW: no maximo isso por membro por
     * dia. RG fora dessa janela nao tem limite.
     */
    public static final int RG_PER_DAY = 2;
    /** Nome no filtro "Evento" dos logs para os pontos dados pelo "Pontos de RG". */
    public static final String RG_OBJECTIVE = "RG";
    public static final Duration RG_BEFORE_CW = Duration.ofMinutes(30);
    private Clock clock = Clock.systemUTC();

    /** So para testes. */
    public void useClock(Clock clock) { this.clock = clock; }

    private final CharacterStatusRepository statusRepository;
    private final MemberRepository memberRepository;
    private final PartyLfgRepository partyLfgRepository;
    private final PointsService pointsService;
    private final AuditLogService auditLogService;
    private final PartyBoardBroadcaster partyBoardBroadcaster;
    private final WebSocketPublisher webSocketPublisher;
    private final GameRegionRepository gameRegionRepository;
    private final JdbcTemplate jdbc;

    /**
     * Regioes conhecidas (catalogo game_region, com 0 membros) mais onde ha gente agora,
     * para os campos do modal. Mais cheios primeiro.
     */
    @Transactional(readOnly = true)
    public List<LocationOption> locations() {
        SecurityUtils.requireRole(Role.MODERADOR);
        Map<List<String>, Set<Long>> byPlace = new HashMap<>();
        for (var known : gameRegionRepository.findAll()) {
            byPlace.putIfAbsent(Arrays.asList(known.map(), known.region()), new HashSet<>());
        }
        for (CharacterStatus s : currentByMember().values()) {
            var key = Arrays.asList(s.getMapName(), s.getRegionName());
            byPlace.computeIfAbsent(key, k -> new HashSet<>()).add(s.getMember().getId());
        }
        return byPlace.entrySet().stream()
                .map(e -> new LocationOption(e.getKey().get(0), e.getKey().get(1), e.getValue().size()))
                .sorted(Comparator.comparingInt(LocationOption::members).reversed()
                        .thenComparing(o -> Objects.toString(o.map(), ""))
                        .thenComparing(o -> Objects.toString(o.region(), "")))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LocationMatch> preview(PreviewRequest request) {
        SecurityUtils.requireRole(Role.MODERADOR);
        return matches(request.map(), request.region(), request.scope());
    }

    @Transactional
    public AwardResult award(AwardRequest request, MemberPrincipal actor) {
        SecurityUtils.requireRole(Role.MODERADOR);
        var current = matches(request.map(), request.region(), request.scope()).stream()
                .collect(Collectors.toMap(LocationMatch::memberId, m -> m));
        var actorEntity = memberRepository.getReferenceById(actor.getId());
        String shownMap = trimToNull(request.mapLabel()) != null ? request.mapLabel().trim() : request.map();
        String place = placeLabel(shownMap, request.region());
        String reason = request.reason() == null || request.reason().isBlank()
                ? (request.pointsType() == PointsType.GENERAL ? "Pontos de RG: " : "Pontos por local: ") + place
                : request.reason().trim() + " (" + place + ")";

        var awarded = new ArrayList<String>();
        var skipped = new ArrayList<String>();
        var limited = new ArrayList<String>();
        // Limite so nos 30 min antes de uma CW (conta pelo dia da CW).
        Optional<CwAttendanceService.Slot> cw = request.pointsType() == PointsType.GENERAL
                ? CwAttendanceService.startingWithin(clock.instant(), RG_BEFORE_CW) : Optional.empty();
        boolean priorityChanged = false;
        // Ordem fixa de ids: dois moderadores confirmando juntos travam as linhas na mesma ordem.
        for (Long memberId : new TreeSet<>(request.memberIds())) {
            LocationMatch match = current.get(memberId);
            if (match == null) {
                memberRepository.findById(memberId).ifPresent(m -> skipped.add(m.getNickname()));
                continue;
            }
            Member member = memberRepository.findByIdForUpdate(memberId).orElseThrow();
            if (request.pointsType() == PointsType.GENERAL) {
                // Linha do membro travada acima: duas confirmacoes juntas nao passam do limite.
                if (cw.isPresent()) {
                    if (rgToday(memberId, cw.get().date()) >= RG_PER_DAY) {
                        limited.add(member.getNickname());
                        continue;
                    }
                    jdbc.update("INSERT INTO rg_award(member_id,day,cw_slot,points,actor_id) VALUES(?,?,?,?,?)",
                            memberId, cw.get().date(), cw.get().window().slot(), request.amount(), actor.getId());
                }
                pointsService.adjustPoints(member, actorEntity, request.amount(), PointsModality.AJUSTE, reason);
                auditLogService.log(AuditLogType.POINTS_ADJUSTED, actorEntity, member, Map.of(
                        "amount", request.amount(),
                        "modality", PointsModality.AJUSTE.name(),
                        "reason", reason,
                        // Pontos gerais por local so saem do "Pontos de RG": aparecem no filtro "Evento" dos logs como RG.
                        "objectiveName", RG_OBJECTIVE,
                        "source", "RG"));
                webSocketPublisher.publishPointsUpdate(memberId, () -> Map.of(
                        "memberId", memberId,
                        "availablePoints", pointsService.getAvailablePoints(memberId)));
            } else {
                int previous = member.getPartyPriorityPoints();
                int next = (int) Math.min(Integer.MAX_VALUE, (long) previous + request.amount());
                member.setPartyPriorityPoints(next);
                memberRepository.save(member);
                auditLogService.log(AuditLogType.PARTY_QUEUE_UPDATED, actorEntity, member, Map.of(
                        "action", "POINTS",
                        "oldPoints", previous,
                        "newPoints", next,
                        "reason", reason));
                priorityChanged = true;
            }
            awarded.add(member.getNickname());
        }
        if (awarded.isEmpty()) {
            if (!limited.isEmpty()) {
                throw new AppException("Todos os marcados já receberam " + RG_PER_DAY + " RG hoje.", HttpStatus.CONFLICT);
            }
            throw new AppException("Ninguém marcado ainda está nesse local. Atualize a prévia.", HttpStatus.CONFLICT);
        }
        if (priorityChanged) partyBoardBroadcaster.boardChanged();
        return new AwardResult(awarded.size(), awarded, skipped, limited);
    }

    private List<LocationMatch> matches(String map, String region, Scope scope) {
        String wantedMap = trimToNull(map);
        String wantedRegion = trimToNull(region);
        if (wantedMap == null && wantedRegion == null) {
            throw new AppException("Escolha o mapa, a região ou os dois.", HttpStatus.BAD_REQUEST);
        }
        Set<Long> queue = partyLfgRepository.findAll().stream()
                .map(PartyLfg::getMemberId)
                .collect(Collectors.toSet());
        Instant now = Instant.now();
        // Contagem so aparece (e so limita) nos 30 min antes de uma CW.
        Map<Long, Integer> rg = CwAttendanceService.startingWithin(clock.instant(), RG_BEFORE_CW)
                .map(slot -> rgTodayByMember(slot.date())).orElse(Map.of());
        return currentByMember().values().stream()
                .filter(s -> wantedMap == null || wantedMap.equalsIgnoreCase(s.getMapName()))
                .filter(s -> wantedRegion == null || wantedRegion.equalsIgnoreCase(s.getRegionName()))
                .filter(s -> scope == Scope.ALL || queue.contains(s.getMember().getId()))
                .map(s -> new LocationMatch(
                        s.getMember().getId(), s.getMember().getNickname(), s.getCharacterName(),
                        s.getMapName(), s.getRegionName(), s.getMapX(), s.getMapY(), s.getLocationAt(),
                        queue.contains(s.getMember().getId()),
                        stillSeconds(s, now),
                        rg.getOrDefault(s.getMember().getId(), 0)))
                .sorted(Comparator.comparing(LocationMatch::nickname, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private int rgToday(Long memberId, LocalDate day) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM rg_award WHERE member_id=? AND day=?", Integer.class, memberId, day);
        return n == null ? 0 : n;
    }

    private Map<Long, Integer> rgTodayByMember(LocalDate day) {
        var out = new HashMap<Long, Integer>();
        jdbc.query("SELECT member_id, count(*) FROM rg_award WHERE day=? GROUP BY member_id",
                rs -> { out.put(rs.getLong(1), rs.getInt(2)); }, day);
        return out;
    }

    /**
     * Local atual de cada membro aprovado: o personagem com envio mais recente.
     * Conta compartilhada (mesmo nick em duas contas) aparece nas duas, como no resto da sincronizacao.
     */
    private Map<Long, CharacterStatus> currentByMember() {
        var latest = new HashMap<Long, CharacterStatus>();
        for (CharacterStatus s : statusRepository.findByLocationAtAfter(Instant.now().minus(FRESH))) {
            if (s.getMember().getStatus() != MemberStatus.APROVADO) continue;
            latest.merge(s.getMember().getId(), s,
                    (a, b) -> a.getLocationAt().isAfter(b.getLocationAt()) ? a : b);
        }
        return latest;
    }

    /** Segundos sem fazer nada (parado e sem EXP); -1 = nao da para saber (DLL sem coordenada): nunca AFK. */
    private static long stillSeconds(CharacterStatus s, Instant now) {
        Instant since = s.idleSince();
        return since == null ? -1 : Math.max(0, Duration.between(since, now).getSeconds());
    }

    private static String placeLabel(String map, String region) {
        String m = trimToNull(map);
        String r = trimToNull(region);
        if (m != null && r != null) return r + " · " + m;
        return m != null ? m : r;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
