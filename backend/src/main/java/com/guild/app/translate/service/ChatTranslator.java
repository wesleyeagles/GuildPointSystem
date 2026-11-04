package com.guild.app.translate.service;

import java.time.Instant;
import java.util.Optional;

/** Tradução russo -> inglês de uma mensagem do chat; vazio quando o serviço falha ou está sem cota. */
public interface ChatTranslator {
    Optional<String> translate(String text);

    /** Caracteres por dia da cota do serviço (0 = sem limite conhecido). */
    default long dailyLimit() { return 0; }

    /** Até quando o serviço está pausado porque a cota acabou (vazio = não está). */
    default Optional<Instant> quotaPausedUntil() { return Optional.empty(); }
}
