package com.guild.app.party.entity;

import com.guild.app.member.entity.Member;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Single row (id = 1). */
@Entity
@Table(name = "party_settings")
@Getter
@Setter
public class PartySettings {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    /** Waiting list closed by Administrador/Líder: members cannot join it on their own. */
    @Column(name = "lfg_locked", nullable = false)
    private boolean lfgLocked;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_id")
    private Member updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
