package com.guild.app.item.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "item_talic")
@Getter
@Setter
public class ItemTalic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "talic_type", nullable = false, length = 50)
    private String talicType;

    @Column(nullable = false)
    private Integer level = 0;

    @Column(nullable = false)
    private Integer slot = 0;
}
