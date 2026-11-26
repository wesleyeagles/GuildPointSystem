package com.guild.app.marker;

import com.guild.app.enemy.service.EnemyRadarService;
import com.guild.app.member.entity.Member;
import com.guild.app.common.enums.MemberStatus;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.shout.service.ShoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Seta de marcação da guild (DLL 1.52.0): Líder/Administrador marca um jogador ou mob no jogo (Ctrl+Shift+clique,
 * Ctrl+Espaço) e a DLL de todos os membros desenha a seta em cima dele. Uma marcação só por vez, em memória: toda
 * DLL pergunta a cada 1 s. O alvo vai como tipo (0 jogador, 1 monstro) e serial do servidor do jogo, iguais em
 * todos os clientes. Some sozinha depois de {@link #LIFETIME}.
 * Sem marcação manual, a seta vai sozinha num jogador de guild inimiga (radar, {@link EnemyRadarService#autoTarget()})
 * até ele morrer; a DLL que vê o alvo morto avisa (GET com dead=id) e a seta pula para o próximo.
 */
@Service
@RequiredArgsConstructor
public class MarkerService {

    public static final Duration LIFETIME = Duration.ofHours(2);
    static final Duration MEMBER_CACHE = Duration.ofMinutes(1);

    /** id muda a cada marcação (a DLL compara); clear = sem seta. */
    public record Mark(long id, int kind, long serial, String author, Instant at) {}

    private record Membership(boolean member, Instant until) {}

    private final ShoutService shouts;
    private final MemberRepository members;
    private final EnemyRadarService enemies;
    Clock clock = Clock.systemUTC();

    private final AtomicLong ids = new AtomicLong(System.currentTimeMillis());
    private final AtomicReference<Mark> current = new AtomicReference<>();
    /** Seta automática do radar (só quando não há marcação manual); o id muda quando o alvo muda. */
    private final AtomicReference<Mark> auto = new AtomicReference<>();
    private final Map<String, Membership> membership = new ConcurrentHashMap<>();

    /** Marcação para a DLL deste personagem (só membro aprovado). */
    public Optional<Mark> forGame(String character) {
        return forGame(character, null);
    }

    /**
     * Marcação para a DLL deste personagem (só membro aprovado). deadId = a DLL viu morto o alvo da marcação com esse
     * id: a manual some; na automática, o radar escolhe o próximo inimigo.
     */
    public Optional<Mark> forGame(String character, Long deadId) {
        if (!member(character)) return Optional.empty();
        if (deadId != null) {
            Mark manual = current.get();
            if (manual != null && manual.id() == deadId && manual.kind() >= 0) current.compareAndSet(manual, null);
            Mark radar = auto.get();
            if (radar != null && radar.id() == deadId) enemies.killed(radar.serial());
        }
        Mark mark = current.get();
        if (mark != null && mark.kind() >= 0 && clock.instant().isBefore(mark.at().plus(LIFETIME))) return Optional.of(mark);
        return autoMark();
    }

    private synchronized Optional<Mark> autoMark() {
        var serial = enemies.autoTarget();
        if (serial.isEmpty()) { auto.set(null); return Optional.empty(); }
        Mark radar = auto.get();
        if (radar == null || radar.serial() != serial.getAsLong()) {
            radar = new Mark(ids.incrementAndGet(), 0, serial.getAsLong(), "radar", clock.instant());
            auto.set(radar);
        }
        return Optional.of(radar);
    }

    /** Marca (kind 0/1, serial &gt; 0) ou tira (kind -1). Vazio = personagem sem permissão. */
    public Optional<Mark> set(String character, int kind, long serial) {
        Optional<Member> author = shouts.sender(character);
        if (author.isEmpty()) return Optional.empty();
        boolean clear = kind < 0;
        if (!clear && (kind > 1 || serial <= 0 || serial > 4_000_000_000L)) throw new IllegalArgumentException("alvo inválido");
        Mark mark = new Mark(ids.incrementAndGet(), clear ? -1 : kind, clear ? 0 : serial, author.get().getNickname(), clock.instant());
        current.set(mark);
        return Optional.of(mark);
    }

    private boolean member(String character) {
        if (character == null || character.isBlank() || character.length() > 32) return false;
        String key = character.strip().toLowerCase();
        Instant now = clock.instant();
        Membership cached = membership.get(key);
        if (cached != null && now.isBefore(cached.until())) return cached.member();
        boolean member = members.findByGameCharacter(character.strip()).stream().anyMatch(m ->
            m.getStatus() == MemberStatus.APROVADO && m.isProfileComplete() && m.getDeletedAt() == null && m.getSuspendedAt() == null);
        if (membership.size() > 5000) membership.clear();
        membership.put(key, new Membership(member, now.plus(MEMBER_CACHE)));
        return member;
    }

    /** Testes: esquece o cache de membros. */
    public void forget() { membership.clear(); }
}
