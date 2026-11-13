package com.guild.app.characterstatus.service;

import com.guild.app.characterstatus.dto.SyncAccessView;
import com.guild.app.characterstatus.entity.CharacterStatus;
import com.guild.app.characterstatus.repository.CharacterStatusRepository;
import com.guild.app.dll.dto.DllReleaseView;
import com.guild.app.dll.service.DllReleaseService;
import com.guild.app.dll.DllVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exige que o membro use a DLL de sincronizacao: depois do prazo, so acessa o site quem enviou
 * dados do jogo dentro da janela. Contas criadas depois do prazo geral ganham um prazo proprio
 * contado do cadastro.
 *
 * Nao prova que a DLL esta instalada (a ingestao nao e autenticada), apenas incentiva o uso.
 */
@Service
public class SyncAccessService {

    /** O filtro consulta a cada requisicao: evita um SELECT por chamada. */
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private record Entry(Optional<Instant> lastSyncAt, long expiresAt) {}
    private record VersionEntry(Optional<String> version, long expiresAt) {}

    private final CharacterStatusRepository statusRepository;
    private final DllReleaseService dllReleaseService;
    private final Instant enforceFrom;
    private final Duration window;
    private final Duration newAccountGrace;
    private final Map<Long, Entry> cache = new ConcurrentHashMap<>();
    private final Map<Long, VersionEntry> versionCache = new ConcurrentHashMap<>();
    /** Abaixo desta versão a DLL não entra no site (403 DLL_OUTDATED), mesmo sincronizando. */
    private final String minDllVersion;

    @Autowired
    public SyncAccessService(
            CharacterStatusRepository statusRepository,
            DllReleaseService dllReleaseService,
            @Value("${guild.sync.enforce-from}") String enforceFrom,
            @Value("${guild.sync.window:3d}") Duration window,
            @Value("${guild.sync.new-account-grace:1d}") Duration newAccountGrace,
            @Value("${guild.dll.min-version:1.2.0}") String minDllVersion) {
        if (!DllVersion.isValid(minDllVersion)) throw new IllegalArgumentException("guild.dll.min-version inválida: " + minDllVersion);
        this.minDllVersion = minDllVersion;
        this.statusRepository = statusRepository;
        this.dllReleaseService = dllReleaseService;
        this.enforceFrom = OffsetDateTime.parse(enforceFrom).toInstant();
        this.window = window;
        this.newAccountGrace = newAccountGrace;
    }

    /** Versão mínima padrão (1.2.0). */
    public SyncAccessService(CharacterStatusRepository statusRepository, DllReleaseService dllReleaseService,
                             String enforceFrom, Duration window, Duration newAccountGrace) {
        this(statusRepository, dllReleaseService, enforceFrom, window, newAccountGrace, "1.2.0");
    }

    public SyncAccessView status(Long memberId, Instant createdAt) {
        Instant now = Instant.now();
        Instant lastSyncAt = lastSyncAt(memberId).orElse(null);
        boolean synced = lastSyncAt != null && lastSyncAt.isAfter(now.minus(window));
        Instant deadline = deadline(createdAt);
        String dllVersion = statusRepository.findFirstByMemberIdOrderByUpdatedAtDesc(memberId)
                .map(CharacterStatus::getDllVersion).orElse(null);
        String latestDllVersion = dllReleaseService.latest().map(DllReleaseView::version).orElse(null);
        boolean dllOutdated = lastSyncAt != null && DllReleaseService.isOutdated(dllVersion, latestDllVersion);
        return new SyncAccessView(
                synced,
                lastSyncAt,
                deadline,
                deadline.isAfter(enforceFrom),
                !synced && !now.isBefore(deadline),
                window.toDays(),
                window.toHours(),
                dllVersion,
                latestDllVersion,
                dllOutdated,
                dllOutdated ? dllReleaseService.releasesNewerThan(dllVersion) : 0,
                minDllVersion,
                lastSyncAt != null && belowMinimum(dllVersion));
    }

    private boolean belowMinimum(String version) {
        return !DllVersion.isValid(version) || DllVersion.compare(version, minDllVersion) < 0;
    }

    /**
     * Usado pelo filtro: sincronizou, mas com DLL abaixo da mínima (ou antiga, sem versão). Quem nunca
     * sincronizou fica com o bloqueio de sincronização, não com este.
     */
    public boolean isDllTooOld(Long memberId) {
        if (lastSyncAt(memberId).isEmpty()) return false;
        return belowMinimum(dllVersion(memberId).orElse(null));
    }

    public String minDllVersion() { return minDllVersion; }

    /** Usado pelo filtro: antes do prazo responde sem tocar no banco. */
    public boolean isBlocked(Long memberId, Instant createdAt) {
        Instant now = Instant.now();
        if (now.isBefore(deadline(createdAt))) return false;
        return lastSyncAt(memberId).map(at -> !at.isAfter(now.minus(window))).orElse(true);
    }

    /** Chamado na ingestao: libera o acesso na hora, sem esperar o cache expirar. */
    public void recordSync(Long memberId, Instant at) {
        cache.put(memberId, new Entry(Optional.of(at), System.currentTimeMillis() + CACHE_TTL.toMillis()));
    }

    /** Idem, com a versão da DLL que enviou: atualizar a DLL libera o site na hora. */
    public void recordSync(Long memberId, Instant at, String dllVersion) {
        recordSync(memberId, at);
        versionCache.put(memberId, new VersionEntry(Optional.ofNullable(dllVersion), System.currentTimeMillis() + CACHE_TTL.toMillis()));
    }

    private Optional<String> dllVersion(Long memberId) {
        long nowMs = System.currentTimeMillis();
        VersionEntry cached = versionCache.get(memberId);
        if (cached != null && cached.expiresAt() > nowMs) return cached.version();
        Optional<String> loaded = statusRepository.findFirstByMemberIdOrderByUpdatedAtDesc(memberId).map(CharacterStatus::getDllVersion);
        versionCache.put(memberId, new VersionEntry(loaded, nowMs + CACHE_TTL.toMillis()));
        return loaded;
    }

    private Instant deadline(Instant createdAt) {
        if (createdAt == null) return enforceFrom;
        Instant ownDeadline = createdAt.plus(newAccountGrace);
        return ownDeadline.isAfter(enforceFrom) ? ownDeadline : enforceFrom;
    }

    private Optional<Instant> lastSyncAt(Long memberId) {
        long nowMs = System.currentTimeMillis();
        Entry cached = cache.get(memberId);
        if (cached != null && cached.expiresAt() > nowMs) return cached.lastSyncAt();
        Optional<Instant> loaded = statusRepository.findLastSyncAt(memberId);
        cache.put(memberId, new Entry(loaded, nowMs + CACHE_TTL.toMillis()));
        return loaded;
    }
}
