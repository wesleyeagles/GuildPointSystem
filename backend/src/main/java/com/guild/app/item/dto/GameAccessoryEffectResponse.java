package com.guild.app.item.dto;

import com.guild.app.common.enums.EffectDisplayType;

import java.math.BigDecimal;

public record GameAccessoryEffectResponse(
        Integer code,
        String name,
        EffectDisplayType displayType,
        BigDecimal rawValue,
        String displayValue
) {}
