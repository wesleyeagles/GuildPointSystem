package com.guild.app.party.dto;

import jakarta.validation.constraints.NotNull;

public record InviteMemberRequest(@NotNull Long memberId) {}
