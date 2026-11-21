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
    public static final int MIN_DISTANCE = 0;
    public static final int MAX_DISTANCE = 200;
    public static final int DEFAULT_DISTANCE = 100;
    public static final String DEFAULT_MARKER_COLOR = "FF2828";
    static final long CACHE_MS = 30_000;

    public record View(int partyArrowScale, int guildArrowScale, int arrowDistance, int markerArrowScale, String markerColor,
                       String updatedBy, Instant updatedAt) {}

    /** O que o Admin manda; markerArrowScale/markerColor nulos = mantém (painel antigo). */
    public record Update(int partyArrowScale, int guildArrowScale, int arrowDistance, Integer markerArrowScale,
                         String markerColor) {}

    /** % de cada seta e da distância até o alvo; 100 = como o jogo desenha. */
    public record Scales(int party, int guild, int distance, int marker, String markerColor) {}

    private static final Scales DEFAULTS = new Scales(DEFAULT_SCALE, DEFAULT_SCALE, DEFAULT_DISTANCE, DEFAULT_SCALE,
                                                      DEFAULT_MARKER_COLOR);

    private final DllDisplaySettingsRepository repository;
    private final MemberRepository members;
    Clock clock = Clock.systemUTC();

    private volatile Scales cached = DEFAULTS;
    private volatile long cachedAt = Long.MIN_VALUE;

    public Scales arrowScales() {
        long now = clock.millis();
        if (cachedAt == Long.MIN_VALUE || now - cachedAt >= CACHE_MS) {
            cached = repository.findById(DllDisplaySettings.SINGLETON_ID)
                    .map(DllDisplaySettingsService::scales).orElse(DEFAULTS);
            cachedAt = now;
        }
        return cached;
    }

    @Transactional(readOnly = true)
    public View view() {
        return repository.findById(DllDisplaySettings.SINGLETON_ID).map(this::toView)
                .orElse(new View(DEFAULT_SCALE, DEFAULT_SCALE, DEFAULT_DISTANCE, DEFAULT_SCALE, DEFAULT_MARKER_COLOR, null, null));
    }

    @Transactional
    public View update(Update u, MemberPrincipal by) {
        DllDisplaySettings s = repository.findById(DllDisplaySettings.SINGLETON_ID).orElseGet(() -> {
            DllDisplaySettings created = new DllDisplaySettings();
            created.setId(DllDisplaySettings.SINGLETON_ID);
            return created;
        });
        s.setPartyArrowScale(clamp(u.partyArrowScale()));
        s.setGuildArrowScale(clamp(u.guildArrowScale()));
        s.setArrowDistance(Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, u.arrowDistance())));
        if (u.markerArrowScale() != null) s.setMarkerArrowScale(clamp(u.markerArrowScale()));
        if (u.markerColor() != null) s.setMarkerColor(color(u.markerColor()));
        s.setUpdatedBy(by == null ? null : members.getReferenceById(by.getId()));
        s.setUpdatedAt(clock.instant());
        repository.save(s);
        cached = scales(s);
        cachedAt = clock.millis();
        return toView(s);
    }

    private static int clamp(int scale) { return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale)); }

    /** "#ff2828" ou "FF2828" -> "FF2828"; inválida -> IllegalArgumentException. */
    static String color(String value) {
        String c = value.strip().replace("#", "").toUpperCase();
        if (!c.matches("[0-9A-F]{6}")) throw new IllegalArgumentException("cor inválida");
        return c;
    }

    private static Scales scales(DllDisplaySettings s) {
        return new Scales(s.getPartyArrowScale(), s.getGuildArrowScale(), s.getArrowDistance(), s.getMarkerArrowScale(),
                          s.getMarkerColor());
    }

    private View toView(DllDisplaySettings s) {
        return new View(s.getPartyArrowScale(), s.getGuildArrowScale(), s.getArrowDistance(), s.getMarkerArrowScale(),
                s.getMarkerColor(),
                s.getUpdatedBy() == null ? null : s.getUpdatedBy().getNickname(), s.getUpdatedAt());
    }
}
