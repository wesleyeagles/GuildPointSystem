package com.guild.app.quest.dto;

import jakarta.validation.constraints.*;

public record BdCompletionRequest(
        @NotBlank @Size(max = 32) String character,
        @NotNull @Pattern(regexp = "Q070613") String code,
        @Positive long observedAt,
        @Min(0) @Max(39) int previousSlot,
        @Min(103) @Max(142) int completedSlot,
        @NotNull @Pattern(regexp = "9bb2778096af9f107c1884ea1e3e010be381abfeff45d821b306f458079eddd0") String definitionHash) {}
