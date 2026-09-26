package com.guild.app.party.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PartyMemberId implements Serializable {
    private Long partyId;
    private Long memberId;
}
