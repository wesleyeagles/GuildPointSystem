package com.guild.app.characterstatus.service;

import com.guild.app.characterstatus.dto.CharacterStatusIngestRequest;
import com.guild.app.characterstatus.dto.CharacterStatusView;
import com.guild.app.characterstatus.entity.CharacterStatus;
import com.guild.app.characterstatus.repository.CharacterStatusRepository;
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

@Service
@RequiredArgsConstructor
public class CharacterStatusService {

    private final CharacterStatusRepository statusRepository;
    private final MemberRepository memberRepository;
    private final WebSocketPublisher webSocketPublisher;
    private final SyncAccessService syncAccessService;
    private final GameRegionRepository gameRegionRepository;

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
        syncAccessService.recordSync(member.getId(), now);

        // Avisa a dashboard (apos o commit) para atualizar sem recarregar a pagina.
        webSocketPublisher.publishCharacterStatus(toView(status));
    }

    /** Envio sem mapa nem regiao (DLL antiga ou tela de carregamento) apaga o local: nunca fica um local velho. */
    private static void applyLocation(CharacterStatus status, CharacterStatusIngestRequest request, Instant now) {
        String map = trimToNull(request.map());
        String region = trimToNull(request.region());
        boolean known = map != null || region != null;
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
                s.getUpdatedAt(), s.getMapName(), s.getRegionName(), s.getLocationAt());
    }
}
