package com.guild.app.item.dto;

import java.util.List;

public record GameWeaponResponse(
        Long id,
        String gameCode,
        String name,
        String weaponType,
        Integer iconId,
        String spriteSheet,
        Integer spriteCols,
        Integer grade,
        String civilMask,
        Integer levelRequired,
        Integer gaMinAf,
        Integer gaMaxAf,
        Integer maMinAf,
        Integer maMaxAf,
        List<GameAccessoryEffectResponse> effects
) {}
