package com.guild.app.event.entity;

import com.guild.app.member.entity.Member;
import com.guild.app.objective.entity.Objective;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "event_claim")
@Getter
@Setter
public class EventClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private GuildEvent event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "objective_id", nullable = false)
    private Objective objective;

    @Column(nullable = false)
    private Long points;

    @Column(name = "claimed_at", nullable = false)
    private Instant claimedAt = Instant.now();

    @Column(nullable = false)
    private boolean denied = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "denied_by_id")
    private Member deniedBy;

    @Column(name = "denied_at")
    private Instant deniedAt;
}
