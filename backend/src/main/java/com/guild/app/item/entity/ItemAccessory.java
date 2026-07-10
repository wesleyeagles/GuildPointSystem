package com.guild.app.item.entity;

import com.guild.app.member.entity.GameRace;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "item_accessory")
@Getter
@Setter
public class ItemAccessory {

    @Id
    @Column(name = "item_id")
    private Long itemId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "item_id")
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id", nullable = false)
    private GameRace race;

    @Column(nullable = false, length = 20)
    private String subtype;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "special_effects", nullable = false, columnDefinition = "jsonb")
    private List<String> specialEffects = List.of();
}
