package com.guild.app.event.dto;

import java.time.Instant;

public record EventClaimResponse(
        Long id,
        String objectiveName,
        Long points,
        Instant claimedAt,
        boolean denied,
        boolean manual
) {}
