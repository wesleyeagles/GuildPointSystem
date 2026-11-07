package com.guild.app.event.service;

import com.guild.app.common.enums.MemberStatus;
import com.guild.app.common.exception.AppException;
import com.guild.app.event.entity.EventClaim;
import com.guild.app.event.repository.EventClaimRepository;
import com.guild.app.event.repository.GuildEventRepository;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Eventos dentro do jogo, pela DLL: o mesmo card do site (contador, senha, resgatar). Como os outros
 * envios da DLL, o membro é achado pelo nome do personagem (sem login), só se aprovado.
 * A DLL consulta a cada poucos segundos: os eventos ativos ficam em memória por {@link #CACHE}, e sem
 * evento ativo a consulta nem procura o membro.
 */
@Service
@RequiredArgsConstructor
public class EventGameService {
    static final Duration CACHE = Duration.ofSeconds(3);
    /** Senhas erradas por membro numa janela de {@link #WRONG_WINDOW}: a senha tem só 4 caracteres. */
    static final int MAX_WRONG = 5;
    static final Duration WRONG_WINDOW = Duration.ofMinutes(1);

    private final GuildEventRepository events;
    private final EventClaimRepository claims;
    private final MemberRepository members;
    private final EventService eventService;
    private final Map<Long, Deque<Instant>> wrong = new ConcurrentHashMap<>();
    private volatile Snapshot snapshot = new Snapshot(List.of(), Instant.EPOCH);
    Clock clock = Clock.systemUTC();

    /** Evento como a DLL mostra: fim em segundos Unix (a DLL conta pelo relógio do site). */
    public record GameEvent(long id, long expiresAtEpoch, long points, String objectiveName) {}

    private record Snapshot(List<GameEvent> events, Instant loadedAt) {}

    /** Eventos ativos que este personagem ainda não resgatou; vazio se não é de membro aprovado. */
    public List<GameEvent> pending(String character) {
        Instant now = clock.instant();
        List<GameEvent> active = active(now).stream()
            .filter(e -> e.expiresAtEpoch() > now.getEpochSecond()).toList();
        if (active.isEmpty()) return List.of();
        Optional<Member> member = member(character);
        if (member.isEmpty()) return List.of();
        Long memberId = member.get().getId();
        return active.stream().filter(e -> !claims.existsByEventIdAndMemberId(e.id(), memberId)).toList();
    }

    /** Resgata com a senha digitada no jogo; erros saem como {@link AppException} com o texto para o card. */
    public EventClaim claim(long eventId, String character, String password) {
        Member member = member(character).orElseThrow(() ->
            new AppException("Personagem sem conta aprovada no site.", HttpStatus.FORBIDDEN));
        String typed = password == null ? "" : password.strip();
        if (typed.length() != 4) throw new AppException("A senha deve ter exatamente 4 caracteres.", HttpStatus.BAD_REQUEST);
        Instant now = clock.instant();
        Deque<Instant> misses = wrong.computeIfAbsent(member.getId(), id -> new ArrayDeque<>());
        synchronized (misses) {
            while (!misses.isEmpty() && !misses.peekFirst().isAfter(now.minus(WRONG_WINDOW))) misses.pollFirst();
            if (misses.size() >= MAX_WRONG)
                throw new AppException("Muitas senhas erradas. Aguarde um minuto.", HttpStatus.TOO_MANY_REQUESTS);
        }
        var event = events.findById(eventId)
            .orElseThrow(() -> new AppException("Evento não encontrado.", HttpStatus.NOT_FOUND));
        if (event.isActive() && event.getExpiresAt().isAfter(now) && !event.getPassword().equals(typed)) {
            synchronized (misses) { misses.addLast(now); }
            throw new AppException("Senha inválida", HttpStatus.BAD_REQUEST);
        }
        return eventService.claimFor(eventId, typed, member.getId(), true);
    }

    private List<GameEvent> active(Instant now) {
        Snapshot current = snapshot;
        if (now.isBefore(current.loadedAt().plus(CACHE)) && !now.isBefore(current.loadedAt())) return current.events();
        List<GameEvent> loaded = events.findByActiveTrueAndExpiresAtAfterOrderByCreatedAtDesc(now).stream()
            .map(e -> new GameEvent(e.getId(), e.getExpiresAt().getEpochSecond(), e.getObjective().getPoints(),
                clean(e.getObjective().getName())))
            .toList();
        snapshot = new Snapshot(loaded, now);
        return loaded;
    }

    /** Testes: esquece os eventos guardados. */
    void forget() {
        snapshot = new Snapshot(List.of(), Instant.EPOCH);
    }

    private Optional<Member> member(String character) {
        if (character == null || character.isBlank() || character.length() > 32) return Optional.empty();
        return members.findByGameCharacter(character.strip()).stream().filter(EventGameService::eligible).findFirst();
    }

    private static boolean eligible(Member member) {
        return member.getStatus() == MemberStatus.APROVADO && member.isProfileComplete()
            && member.getDeletedAt() == null && member.getSuspendedAt() == null;
    }

    /** Uma linha só: a resposta da DLL é separada por TAB e quebra de linha. */
    static String clean(String text) {
        if (text == null) return "";
        return text.replaceAll("[\\p{Cntrl}\\u2028\\u2029]+", " ").replaceAll(" {2,}", " ").trim();
    }
}
