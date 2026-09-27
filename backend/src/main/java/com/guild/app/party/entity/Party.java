package com.guild.app.party.entity;

import com.guild.app.common.enums.PartyMap;
import com.guild.app.member.entity.Member;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "party")
@Getter
@Setter
public class Party {

    public static final int MAX_MEMBERS = 8;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PartyMap map;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_id")
    private Member leader;

    @Column(length = 200)
    private String spot;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
