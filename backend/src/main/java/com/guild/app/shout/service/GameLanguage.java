package com.guild.app.shout.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Idioma da DLL por personagem: "en" para membros com a tag "English Player", "pt" para os outros. Toda DLL
 * pergunta pelo shout a cada 5 s, então a lista fica em memória (relida a cada {@link #REFRESH} ou na hora
 * em que a tag muda) e a consulta não toca no banco.
 */
@Component
public class GameLanguage {
    public static final String HEADER = "X-Cerberus-Lang";
    static final Duration REFRESH = Duration.ofMinutes(1);

    private final JdbcTemplate jdbc;
    Clock clock = Clock.systemUTC();
    private volatile Set<String> english;
    private volatile Instant loadedAt = Instant.EPOCH;

    public GameLanguage(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isEnglish(String character) {
        if (character == null || character.isBlank() || character.length() > 32) return false;
        return english().contains(character.strip().toLowerCase(Locale.ROOT));
    }

    /** Há alguém com a tag (senão nem vale traduzir os shouts). */
    public boolean anyEnglish() {
        return !english().isEmpty();
    }

    /** Valor do cabeçalho {@link #HEADER} para a DLL. */
    public String code(String character) {
        return isEnglish(character) ? "en" : "pt";
    }

    /** Avisos do site que a DLL mostra como vieram (ex.: erro do resgate do card de evento), em inglês. */
    static final Map<String, String> ENGLISH = Map.ofEntries(
        Map.entry("Personagem sem conta aprovada no site.", "This character has no approved account on the site."),
        Map.entry("A senha deve ter exatamente 4 caracteres.", "The password must have exactly 4 characters."),
        Map.entry("Muitas senhas erradas. Aguarde um minuto.", "Too many wrong passwords. Wait a minute."),
        Map.entry("Evento não encontrado.", "Event not found."),
        Map.entry("Senha inválida", "Wrong password"),
        Map.entry("Evento expirado", "Event expired"),
        Map.entry("Membro não encontrado.", "Member not found."),
        Map.entry("Você já resgatou este evento", "You already claimed this event"),
        Map.entry("Limite diário deste objetivo atingido", "Daily limit for this objective reached"));

    /** O aviso no idioma do personagem; sem tradução conhecida vai como está. */
    public String text(String character, String portuguese) {
        if (portuguese == null || !isEnglish(character)) return portuguese;
        return ENGLISH.getOrDefault(portuguese.strip(), portuguese);
    }

    /** A tag mudou: relê na próxima consulta. */
    public void forget() {
        english = null;
    }

    private Set<String> english() {
        Set<String> set = english;
        Instant now = clock.instant();
        if (set == null || !now.isBefore(loadedAt.plus(REFRESH))) {
            Set<String> loaded = new HashSet<>();
            jdbc.query("SELECT nickname FROM member WHERE english_player AND status <> 'EXCLUIDO'",
                r -> { loaded.add(r.getString(1).strip().toLowerCase(Locale.ROOT)); });
            english = set = Set.copyOf(loaded);
            loadedAt = now;
        }
        return set;
    }
}
