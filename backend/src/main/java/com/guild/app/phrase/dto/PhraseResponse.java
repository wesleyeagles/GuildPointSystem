package com.guild.app.phrase.dto;

import java.time.Instant;
import java.util.List;

public record PhraseResponse(
        Long id,
        String text,
        Long memberId,
        String memberNickname,
        String memberAvatarUrl,
        /** Nick do membro ligado ou, sem membro, o nome anotado. */
        String author,
        Instant saidAt,
        long votes,
        List<String> voters
) {}
