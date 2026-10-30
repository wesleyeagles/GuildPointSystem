package com.guild.app.characterstatus.dto;

import java.time.Instant;

/** Linha do ranking da guilda / status proprio. */
public record CharacterStatusView(
        Long memberId,
        String memberNickname,
        Long gid,
        String characterName,
        Integer level,
        Float expPercent,
        Integer hp,
        Integer hpMax,
        Integer fp,
        Integer fpMax,
        Integer sp,
        Integer spMax,
        Instant updatedAt,
        /** Local no ultimo envio (mapa, regiao do mini-mapa); null = DLL sem leitura de local. */
        String mapName,
        String regionName,
        Instant locationAt
) {}
