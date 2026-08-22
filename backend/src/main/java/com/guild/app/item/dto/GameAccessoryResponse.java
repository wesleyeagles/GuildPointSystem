package com.guild.app.item.dto;

import java.util.List;

public record GameAccessoryResponse(
        Long id,
        String gameCode,
        String name,
        String subtype,
        Integer iconId,
        String spriteSheet,
        Integer grade,
        String civilMask,
        Integer levelRequired,
        Integer fire,
        Integer water,
        Integer soil,
        Integer wind,
        List<GameAccessoryEffectResponse> effects
) {}
