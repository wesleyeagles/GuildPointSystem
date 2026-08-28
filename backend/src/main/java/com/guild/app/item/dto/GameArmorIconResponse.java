package com.guild.app.item.dto;

public record GameArmorIconResponse(
        String gameCode,
        String name,
        Integer iconId,
        String spriteSheet,
        Integer spriteCols
) {}
