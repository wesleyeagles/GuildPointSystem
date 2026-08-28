package com.guild.app.item.dto;

public record GameAccessoryIconResponse(
        String gameCode,
        String name,
        Integer iconId,
        String spriteSheet
) {}
