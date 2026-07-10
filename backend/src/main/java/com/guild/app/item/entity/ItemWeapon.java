package com.guild.app.item.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "item_weapon")
@Getter
@Setter
public class ItemWeapon {

    @Id
    @Column(name = "item_id")
    private Long itemId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "item_id")
    private Item item;

    @Column(nullable = false)
    private Integer level = 0;

    @Column(nullable = false, length = 50)
    private String subtype;

    @Column(name = "attack_min", nullable = false)
    private Integer attackMin = 0;

    @Column(name = "attack_max", nullable = false)
    private Integer attackMax = 0;

    @Column(name = "force_attack_min", nullable = false)
    private Integer forceAttackMin = 0;

    @Column(name = "force_attack_max", nullable = false)
    private Integer forceAttackMax = 0;

    @Column(name = "cast_name", length = 100)
    private String castName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "special_effects", nullable = false, columnDefinition = "jsonb")
    private List<String> specialEffects = List.of();
}
