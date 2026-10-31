package com.guild.app.party.dto;

import jakarta.validation.constraints.NotNull;

public record UpdatePartyQueueAccessRequest(@NotNull Boolean open) {}
