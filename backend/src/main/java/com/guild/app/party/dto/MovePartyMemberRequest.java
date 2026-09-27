package com.guild.app.party.dto;

import jakarta.validation.constraints.NotNull;

public record MovePartyMemberRequest(
        @NotNull Long memberId,
        /** null = membro vem da lista de espera global */
        Long fromPartyId
) {}
