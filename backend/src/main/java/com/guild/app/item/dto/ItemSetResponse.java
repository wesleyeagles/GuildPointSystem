package com.guild.app.item.dto;

import java.util.List;

public record ItemSetResponse(
        Long id,
        String setCode,
        String civilMask,
        String head,
        String upper,
        String lower,
        String shoes,
        String gauntlet,
        String weapon,
        String shield,
        String amul1,
        String amul2,
        String ring1,
        String ring2,
        String cloack,
        List<ItemSetEffectResponse> effects
) {}
