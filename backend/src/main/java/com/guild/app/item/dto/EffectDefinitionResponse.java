package com.guild.app.item.dto;

import com.guild.app.common.enums.EffectDisplayType;

public record EffectDefinitionResponse(
        Integer code,
        String name,
        EffectDisplayType displayType
) {}
