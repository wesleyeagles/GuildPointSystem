package com.guild.app.member.entity;

import com.guild.app.auth.security.MemberCacheEvictionListener;
import com.guild.app.common.enums.MemberStatus;
import com.guild.app.common.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Entity
@Table(name = "member")
@EntityListeners(MemberCacheEvictionListener.class)
@Getter
@Setter
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "discord_id", unique = true)
    private String discordId;

    @Column(nullable = false, length = 100)
    private String nickname;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id")
    private GameRace race;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id")
    private CharacterClass characterClass;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.MEMBRO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status = MemberStatus.PENDENTE;

    @Column(nullable = false)
    private Long points = 0L;

    @Column(name = "party_priority_points", nullable = false)
    private int partyPriorityPoints;

    @Column(name = "level", nullable = false)
    private Integer level = 1;

    @Column(name = "profile_complete", nullable = false)
    private boolean profileComplete = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "suspension_reason", length = 500)
    private String suspensionReason;

    /** Last day (inclusive, Brasília) the game account is blocked for sale; null = not blocked. */
    @Column(name = "blocked_for_sell_until")
    private LocalDate blockedForSellUntil;

    /** The block lifts by itself once its last day has passed. */
    public boolean isBlockedForSellAccount() {
        return blockedForSellUntil != null
                && !blockedForSellUntil.isBefore(LocalDate.now(ZoneId.of("America/Sao_Paulo")));
    }

    @PrePersist
    void onCreate() {
        if (level == null) {
            level = 1;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
