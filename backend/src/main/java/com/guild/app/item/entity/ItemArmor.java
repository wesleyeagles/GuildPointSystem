package com.guild.app.item.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "item_armor")
@Getter
@Setter
public class ItemArmor {

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

    @Column(name = "armor_class", nullable = false, length = 50)
    private String armorClass;

    @Column(name = "avg_def_power", nullable = false)
    private Integer avgDefPower = 0;

    @Column(name = "defense_success_rate", nullable = false)
    private Integer defenseSuccessRate = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "special_effects", nullable = false, columnDefinition = "jsonb")
    private List<String> specialEffects = List.of();
}
