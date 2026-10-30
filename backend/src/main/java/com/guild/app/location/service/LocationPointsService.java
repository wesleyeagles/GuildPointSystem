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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
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

    private final CharacterStatusRepository statusRepository;
    private final MemberRepository memberRepository;
    private final PartyLfgRepository partyLfgRepository;
    private final PointsService pointsService;
    private final AuditLogService auditLogService;
    private final PartyBoardBroadcaster partyBoardBroadcaster;
    private final WebSocketPublisher webSocketPublisher;
    private final GameRegionRepository gameRegionRepository;

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
                ? "Pontos por local: " + place
                : request.reason().trim() + " (" + place + ")";

        var awarded = new ArrayList<String>();
        var skipped = new ArrayList<String>();
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
                pointsService.adjustPoints(member, actorEntity, request.amount(), PointsModality.AJUSTE, reason);
                auditLogService.log(AuditLogType.POINTS_ADJUSTED, actorEntity, member, Map.of(
                        "amount", request.amount(),
                        "modality", PointsModality.AJUSTE.name(),
                        "reason", reason));
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
            throw new AppException("Ninguém marcado ainda está nesse local. Atualize a prévia.", HttpStatus.CONFLICT);
        }
        if (priorityChanged) partyBoardBroadcaster.boardChanged();
        return new AwardResult(awarded.size(), awarded, skipped);
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
        return currentByMember().values().stream()
                .filter(s -> wantedMap == null || wantedMap.equalsIgnoreCase(s.getMapName()))
                .filter(s -> wantedRegion == null || wantedRegion.equalsIgnoreCase(s.getRegionName()))
                .filter(s -> scope == Scope.ALL || queue.contains(s.getMember().getId()))
                .map(s -> new LocationMatch(
                        s.getMember().getId(), s.getMember().getNickname(), s.getCharacterName(),
                        s.getMapName(), s.getRegionName(), s.getMapX(), s.getMapY(), s.getLocationAt(),
                        queue.contains(s.getMember().getId())))
                .sorted(Comparator.comparing(LocationMatch::nickname, String.CASE_INSENSITIVE_ORDER))
                .toList();
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
