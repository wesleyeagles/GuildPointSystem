package com.guild.app.dll.entity;

import com.guild.app.member.entity.Member;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Ajustes visuais da DLL escolhidos no Admin. Linha única (id = 1). */
@Entity
@Table(name = "dll_display_settings")
@Getter
@Setter
public class DllDisplaySettings {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    /** Tamanho da seta azul da PT do jogo, em % do original (50..500). */
    @Column(name = "party_arrow_scale", nullable = false)
    private int partyArrowScale = 100;

    /** Tamanho da seta da guild do jogo, em % do original (50..500). */
    @Column(name = "guild_arrow_scale", nullable = false)
    private int guildArrowScale = 100;

    /** Distância das setas até o alvo, em % da distância normal (0..200; 100 = como no tamanho normal). */
    @Column(name = "arrow_distance", nullable = false)
    private int arrowDistance = 100;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_id")
    private Member updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
