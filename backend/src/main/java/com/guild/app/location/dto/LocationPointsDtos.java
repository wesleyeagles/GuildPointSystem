package com.guild.app.location.dto;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

/** Tipos da tela "Pontos por local" (Party, Moderador+). */
public final class LocationPointsDtos {

    private LocationPointsDtos() {}

    /** Quem recebe: so quem esta na lista de espera da Party, ou qualquer membro no local. */
    public enum Scope { QUEUE, ALL }

    /** Que ponto e dado: prioridade da fila da Party, ou pontos gerais do site (ranking/leiloes). */
    public enum PointsType { PRIORITY, GENERAL }

    /** Um mapa/regiao com gente agora, para preencher o modal. */
    public record LocationOption(String map, String region, int members) {}

    /** Membro encontrado no local (previa antes de confirmar). */
    public record LocationMatch(
            Long memberId,
            String nickname,
            String characterName,
            String map,
            String region,
            Integer mapX,
            Integer mapY,
            Instant locationAt,
            boolean inQueue
    ) {}

    public record PreviewRequest(
            @Size(max = 64) String map,
            @Size(max = 64) String region,
            @NotNull Scope scope
    ) {}

    /**
     * Confirma a previa. {@code memberIds} e o que o moderador marcou: so recebe quem ainda
     * estiver no local na hora de confirmar.
     */
    public record AwardRequest(
            @Size(max = 64) String map,
            @Size(max = 64) String region,
            @NotNull Scope scope,
            @NotNull PointsType pointsType,
            @NotNull @Min(1) @Max(1000) Integer amount,
            @NotEmpty List<@NotNull Long> memberIds,
            @Size(max = 200) String reason,
            /** Nome do mapa como a guild chama ("Crag Mine Field"), so para o motivo no log. */
            @Size(max = 64) String mapLabel
    ) {}

    public record AwardResult(int awarded, List<String> nicknames, List<String> skipped) {}
}
