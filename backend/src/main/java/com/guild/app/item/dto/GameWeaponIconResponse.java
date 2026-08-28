package com.guild.app.item.dto;

public record GameWeaponIconResponse(
        String gameCode,
        String name,
        Integer iconId,
        String spriteSheet,
        Integer spriteCols
) {}
