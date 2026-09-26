package com.guild.app.party.entity;

import com.guild.app.common.enums.PartyPendingKind;
import com.guild.app.common.enums.PartyPendingStatus;
import com.guild.app.member.entity.Member;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "party_pending")
@Getter
@Setter
public class PartyPending {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PartyPendingKind kind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiator_id", nullable = false)
    private Member initiator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id", nullable = false)
    private Member target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PartyPendingStatus status = PartyPendingStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
