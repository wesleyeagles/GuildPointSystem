package com.guild.app.item.entity;

import com.guild.app.common.enums.EffectDisplayType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "effect_definition")
@Getter
@Setter
public class EffectDefinition {

    @Id
    @Column(name = "code")
    private Integer code;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "display_type", nullable = false, length = 20)
    private EffectDisplayType displayType = EffectDisplayType.PERCENT_100;
}
