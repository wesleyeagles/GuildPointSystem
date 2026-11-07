package com.guild.app.phrase.dto;

import java.util.List;

/** Todas as frases (ordem de cadastro) e os votos de quem pediu. */
public record PhraseBoardResponse(
        int maxVotes,
        List<Long> myVotes,
        List<PhraseResponse> phrases
) {}
