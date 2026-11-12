package com.guild.app.translate.service;

import java.time.Instant;
import java.util.Optional;

/** Tradução russo -> inglês de uma mensagem do chat; vazio quando o serviço falha ou está sem cota. */
public interface ChatTranslator {
    Optional<String> translate(String text);

    /** Tradução entre outro par de idiomas ("pt|en"); vazio se o serviço não tem esse par ou falhou. */
    default Optional<String> translate(String text, String langpair) { return Optional.empty(); }

    /** Caracteres por dia da cota do serviço (0 = sem limite conhecido). */
    default long dailyLimit() { return 0; }

    /** Até quando o serviço está pausado porque a cota acabou (vazio = não está). */
    default Optional<Instant> quotaPausedUntil() { return Optional.empty(); }

    /** Problema de configuração para mostrar no painel (ex.: e-mail recusado). */
    default Optional<String> warning() { return Optional.empty(); }
}
