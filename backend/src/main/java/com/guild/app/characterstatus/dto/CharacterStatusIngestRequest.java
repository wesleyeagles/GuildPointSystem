package com.guild.app.characterstatus.dto;

import jakarta.validation.constraints.*;

/**
 * Corpo enviado pelo cliente CerberusBags. As chaves correspondem exatamente ao
 * JSON montado na DLL (gid, character, level, expPercent, hp, hpMax, ...).
 * O local (map, region, mapX, mapY) e opcional: DLLs antigas nao enviam.
 */
public record CharacterStatusIngestRequest(
        @NotNull @Positive Long gid,
        @NotBlank @Size(max = 32) String character,
        @NotNull @Min(1) @Max(200) Integer level,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") Float expPercent,
        @NotNull @Min(0) Integer hp,
        @NotNull @Min(1) Integer hpMax,
        @NotNull @Min(0) Integer fp,
        @NotNull @Min(0) Integer fpMax,
        @NotNull @Min(0) Integer sp,
        @NotNull @Min(0) Integer spMax,
        @Size(max = 64) String map,
        @Size(max = 64) String region,
        @Min(0) @Max(9999) Integer mapX,
        @Min(0) @Max(9999) Integer mapY,
        /** Segundos desde a ultima tecla, clique, roda do mouse ou camera girando no jogo (DLL 1.30.0+; null = nao envia). */
        @Min(0) Integer idleSec
) {
    /** Sem {@code idleSec} (DLL antes da 1.30.0). */
    public CharacterStatusIngestRequest(Long gid, String character, Integer level, Float expPercent,
                                        Integer hp, Integer hpMax, Integer fp, Integer fpMax,
                                        Integer sp, Integer spMax, String map, String region, Integer mapX, Integer mapY) {
        this(gid, character, level, expPercent, hp, hpMax, fp, fpMax, sp, spMax, map, region, mapX, mapY, null);
    }

    /** Construtor do formato antigo (sem local), usado pelos testes e DLLs anteriores. */
    public CharacterStatusIngestRequest(Long gid, String character, Integer level, Float expPercent,
                                        Integer hp, Integer hpMax, Integer fp, Integer fpMax,
                                        Integer sp, Integer spMax) {
        this(gid, character, level, expPercent, hp, hpMax, fp, fpMax, sp, spMax, null, null, null, null, null);
    }
}
