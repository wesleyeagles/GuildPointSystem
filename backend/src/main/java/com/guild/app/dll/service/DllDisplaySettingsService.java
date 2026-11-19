package com.guild.app.dll.service;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.dll.entity.DllDisplaySettings;
import com.guild.app.dll.repository.DllDisplaySettingsRepository;
import com.guild.app.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Ajustes visuais da DLL (Admin -> DLL): tamanho das setas da PT e da guild do jogo. Toda DLL pergunta o shout a
 * cada 5 s e os valores vão nos headers da resposta, então ficam em memória e o banco é relido no máximo a cada
 * 30 s (vale também com mais de uma instância do backend).
 */
@Service
@RequiredArgsConstructor
public class DllDisplaySettingsService {

    public static final int MIN_SCALE = 50;
    public static final int MAX_SCALE = 500;
    public static final int DEFAULT_SCALE = 100;
    static final long CACHE_MS = 30_000;

    public record View(int partyArrowScale, int guildArrowScale, String updatedBy, Instant updatedAt) {}

    /** % de cada seta; 100 = como o jogo desenha. */
    public record Scales(int party, int guild) {}

    private static final Scales DEFAULTS = new Scales(DEFAULT_SCALE, DEFAULT_SCALE);

    private final DllDisplaySettingsRepository repository;
    private final MemberRepository members;
    Clock clock = Clock.systemUTC();

    private volatile Scales cached = DEFAULTS;
    private volatile long cachedAt = Long.MIN_VALUE;

    public Scales arrowScales() {
        long now = clock.millis();
        if (cachedAt == Long.MIN_VALUE || now - cachedAt >= CACHE_MS) {
            cached = repository.findById(DllDisplaySettings.SINGLETON_ID)
                    .map(s -> new Scales(s.getPartyArrowScale(), s.getGuildArrowScale())).orElse(DEFAULTS);
            cachedAt = now;
        }
        return cached;
    }

    @Transactional(readOnly = true)
    public View view() {
        return repository.findById(DllDisplaySettings.SINGLETON_ID).map(this::toView)
                .orElse(new View(DEFAULT_SCALE, DEFAULT_SCALE, null, null));
    }

    @Transactional
    public View update(int partyArrowScale, int guildArrowScale, MemberPrincipal by) {
        DllDisplaySettings s = repository.findById(DllDisplaySettings.SINGLETON_ID).orElseGet(() -> {
            DllDisplaySettings created = new DllDisplaySettings();
            created.setId(DllDisplaySettings.SINGLETON_ID);
            return created;
        });
        s.setPartyArrowScale(clamp(partyArrowScale));
        s.setGuildArrowScale(clamp(guildArrowScale));
        s.setUpdatedBy(by == null ? null : members.getReferenceById(by.getId()));
        s.setUpdatedAt(clock.instant());
        repository.save(s);
        cached = new Scales(s.getPartyArrowScale(), s.getGuildArrowScale());
        cachedAt = clock.millis();
        return toView(s);
    }

    private static int clamp(int scale) { return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale)); }

    private View toView(DllDisplaySettings s) {
        return new View(s.getPartyArrowScale(), s.getGuildArrowScale(),
                s.getUpdatedBy() == null ? null : s.getUpdatedBy().getNickname(), s.getUpdatedAt());
    }
}
