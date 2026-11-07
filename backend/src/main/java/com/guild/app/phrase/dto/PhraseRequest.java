package com.guild.app.phrase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record PhraseRequest(
        @NotBlank(message = "Escreva a frase.")
        @Size(max = 300, message = "A frase pode ter no máximo 300 caracteres.")
        String text,

        @NotNull(message = "Escolha o membro que falou a frase.")
        Long memberId,

        /** Quando foi dita; vazio = agora. */
        Instant saidAt
) {}
