package com.guild.app.item.dto;

import java.math.BigDecimal;
import java.util.List;

public record GameArmorResponse(
        Long id,
        String gameCode,
        String name,
        String slot,
        Integer iconId,
        String spriteSheet,
        Integer spriteCols,
        Integer grade,
        String civilMask,
        Integer levelRequired,
        Integer defFc,
        BigDecimal defFacing,
        Integer defFacingDisplay,
        List<GameAccessoryEffectResponse> effects
) {}
