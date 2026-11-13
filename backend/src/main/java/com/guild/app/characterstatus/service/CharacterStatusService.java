package com.guild.app.characterstatus.service;

import com.guild.app.characterstatus.dto.CharacterStatusIngestRequest;
import com.guild.app.characterstatus.dto.CharacterStatusView;
import com.guild.app.characterstatus.entity.CharacterStatus;
import com.guild.app.characterstatus.repository.CharacterStatusRepository;
import com.guild.app.cw.service.CwAttendanceService;
import com.guild.app.pbauto.service.PbAutoService;
import com.guild.app.locationhistory.service.LocationHistoryService;
import com.guild.app.location.repository.GameRegionRepository;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.websocket.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CharacterStatusService {

    private final CharacterStatusRepository statusRepository;
    private final MemberRepository memberRepository;
    private final WebSocketPublisher webSocketPublisher;
    private final SyncAccessService syncAccessService;
    private final GameRegionRepository gameRegionRepository;
    private final CwAttendanceService cwAttendanceService;
    private final PbAutoService pbAutoService;
    private final LocationHistoryService locationHistoryService;

    // ---- Ingestao (sem autenticacao; associada pelo nome do personagem) ----
    // Todas as contas com esse nick recebem o envio (personagem compartilhado).
    // NAO autenticado por decisao de projeto: o nome do personagem (unico no jogo)
    // casa com o nickname do membro. Isso identifica o membro, mas nao prova origem:
    // qualquer requisicao HTTP com um nickname valido e aceita. Aceito conscientemente
    // para uma guilda de confianca; o dado e apenas exibicao, nunca pontos/recompensa
    // automaticos. O local so vira ponto quando um moderador confere a previa e confirma
    // (LocationPointsService).

    @Transactional
    public void ingest(CharacterStatusIngestRequest request, String dllVersion) {
        List<Member> members = memberRepository.findByGameCharacter(request.character());
        if (members.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nenhum membro com esse nome de personagem");
        }

        // Coerencia extra alem das validacoes do DTO: dado do cliente nao e confiavel.
        if (request.hp() > request.hpMax() || request.fp() > request.fpMax() || request.sp() > request.spMax()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Valores inconsistentes");
        }

        Instant now = Instant.now();
        for (Member member : members) save(member, request, dllVersion, now);

        String map = trimToNull(request.map());
        String region = trimToNull(request.region());
        if (map != null && region != null) gameRegionRepository.seen(map, region, now);
    }

    private void save(Member member, CharacterStatusIngestRequest request, String dllVersion, Instant now) {
        CharacterStatus status = statusRepository
                .findByMemberIdAndGid(member.getId(), request.gid())
                .orElseGet(() -> {
                    CharacterStatus created = new CharacterStatus();
                    created.setMember(member);
                    created.setGid(request.gid());
                    return created;
                });

        if (gainedExp(status, request)) status.setExpGainedAt(now);
        Instant active = activeAt(status, request, now);
        if (active != null && (status.getActiveAt() == null || active.isAfter(status.getActiveAt()))) status.setActiveAt(active);
        status.setCharacterName(request.character());
        status.setDllVersion(dllVersion);
        status.setLevel(request.level());
        status.setExpPercent(request.expPercent());
        status.setHp(request.hp());
        status.setHpMax(request.hpMax());
        status.setFp(request.fp());
        status.setFpMax(request.fpMax());
        status.setSp(request.sp());
        status.setSpMax(request.spMax());
        applyLocation(status, request, now);
        status.setObservedAt(now);
        status.setUpdatedAt(now);
        statusRepository.save(status);
        syncAccessService.recordSync(member.getId(), now, dllVersion);
        // Durante a CW: tempo no mapa da CW (sem contar AFK), para a aba Presença em CW.
        cwAttendanceService.record(member.getId(), request.character(), status.getMapName(), status.idleSince(),
                status.getMapX(), status.getMapY(), request.fp(), status.getExpGainedAt(), now);
        // PBs diarios automaticos: presenca nas regioes do PB durante a janela.
        pbAutoService.record(member.getId(), request.character(), status.getMapName(), status.getRegionName(), now);
        // Historico de local (3 dias) para a staff consultar onde alguem estava num horario.
        locationHistoryService.record(member.getId(), request.character(), now, status.getMapName(), status.getRegionName(),
                status.getMapX(), status.getMapY(), status.idleSince(), dllVersion);

        // Avisa a dashboard (apos o commit) para atualizar sem recarregar a pagina.
        webSocketPublisher.publishCharacterStatus(toView(status));
    }

    /** Level ou % maior que no envio anterior deste personagem (morte, que tira EXP, nao conta). */
    private static boolean gainedExp(CharacterStatus status, CharacterStatusIngestRequest request) {
        if (status.getId() == null || status.getLevel() == null || status.getExpPercent() == null) return false;
        int level = request.level();
        return level > status.getLevel() || (level == status.getLevel() && request.expPercent() > status.getExpPercent());
    }

    /** A DLL so conta tecla/clique dos ultimos minutos; mais que isso e so "parado ha muito tempo". */
    static final int MAX_IDLE_SEC = 24 * 3600;

    /**
     * Quando fez algo pela ultima vez, mesmo parado no lugar: HP, FP ou SP menor que no envio anterior (usou skill
     * ou apanhou; regeneracao e pocao so sobem) ou a ultima tecla/clique/camera que a DLL viu. Null = nada novo.
     */
    private static Instant activeAt(CharacterStatus status, CharacterStatusIngestRequest request, Instant now) {
        Instant at = null;
        if (status.getId() != null && status.getHp() != null && status.getFp() != null && status.getSp() != null
                && (request.hp() < status.getHp() || request.fp() < status.getFp() || request.sp() < status.getSp())) at = now;
        if (at == null && request.idleSec() != null && request.idleSec() <= MAX_IDLE_SEC) at = now.minusSeconds(request.idleSec());
        return at;
    }

    /** Envio sem mapa nem regiao (DLL antiga ou tela de carregamento) apaga o local: nunca fica um local velho. */
    private static void applyLocation(CharacterStatus status, CharacterStatusIngestRequest request, Instant now) {
        String map = trimToNull(request.map());
        String region = trimToNull(request.region());
        boolean known = map != null || region != null;
        Integer x = known ? request.mapX() : null;
        Integer y = known ? request.mapY() : null;
        // So conta "parado" com coordenada: sem X/Y (DLL antes da 1.26.2) seria so "na mesma regiao", e quem
        // anda e mata mob pela regiao inteira viraria AFK.
        boolean coords = known && x != null && y != null;
        boolean samePlace = coords
                && status.getStillSince() != null
                && Objects.equals(map, status.getMapName())
                && Objects.equals(region, status.getRegionName())
                && Objects.equals(x, status.getMapX())
                && Objects.equals(y, status.getMapY());
        if (!samePlace) status.setStillSince(coords ? now : null);
        status.setMapName(map);
        status.setRegionName(region);
        status.setMapX(known ? request.mapX() : null);
        status.setMapY(known ? request.mapY() : null);
        status.setLocationAt(known ? now : null);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // ---- Leitura (membro autenticado) ----

    @Transactional(readOnly = true)
    public List<CharacterStatusView> ranking() {
        return statusRepository.findAllByOrderByLevelDescExpPercentDesc().stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CharacterStatusView> myStatuses(Long memberId) {
        return statusRepository.findByMemberIdOrderByLevelDescExpPercentDesc(memberId).stream()
                .map(this::toView)
                .toList();
    }

    private CharacterStatusView toView(CharacterStatus s) {
        return new CharacterStatusView(
                s.getMember().getId(), s.getMember().getNickname(),
                s.getGid(), s.getCharacterName(), s.getLevel(), s.getExpPercent(),
                s.getHp(), s.getHpMax(), s.getFp(), s.getFpMax(), s.getSp(), s.getSpMax(),
                s.getUpdatedAt(), s.getMapName(), s.getRegionName(), s.getLocationAt(), s.getDllVersion());
    }
}
