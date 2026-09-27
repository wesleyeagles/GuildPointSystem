package com.guild.app.party.dto;

import jakarta.validation.constraints.NotNull;

public record AddPartyMemberRequest(@NotNull Long memberId) {}
