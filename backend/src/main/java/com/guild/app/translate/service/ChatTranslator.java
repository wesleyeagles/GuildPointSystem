package com.guild.app.translate.service;

import java.util.Optional;

/** Tradução de uma linha do chat do jogo (russo -> inglês). Vazio = não traduziu (erro, cota ou sem resposta). */
public interface ChatTranslator {
    Optional<String> translate(String text);
}
