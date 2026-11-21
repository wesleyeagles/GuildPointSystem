package com.guild.app.marker;

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
    Clock clock = Clock.systemUTC();

    private final AtomicLong ids = new AtomicLong(System.currentTimeMillis());
    private final AtomicReference<Mark> current = new AtomicReference<>();
    private final Map<String, Membership> membership = new ConcurrentHashMap<>();

    /** Marcação para a DLL deste personagem (só membro aprovado). */
    public Optional<Mark> forGame(String character) {
        Mark mark = current.get();
        if (mark == null || mark.kind() < 0 || !clock.instant().isBefore(mark.at().plus(LIFETIME))) return Optional.empty();
        return member(character) ? Optional.of(mark) : Optional.empty();
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
