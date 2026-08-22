package com.guild.app.item.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "item_set")
@Getter
@Setter
public class ItemSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "set_code", nullable = false, length = 50)
    private String setCode;

    @Column(name = "civil_mask", length = 20)
    private String civilMask;

    @Column(length = 50)
    private String head;

    @Column(length = 50)
    private String upper;

    @Column(length = 50)
    private String lower;

    @Column(length = 50)
    private String shoes;

    @Column(length = 50)
    private String gauntlet;

    @Column(length = 50)
    private String weapon;

    @Column(length = 50)
    private String shield;

    @Column(length = 50)
    private String amul1;

    @Column(length = 50)
    private String amul2;

    @Column(length = 50)
    private String ring1;

    @Column(length = 50)
    private String ring2;

    @Column(length = 50)
    private String cloack;

    @Column(name = "eff1_code")
    private Integer eff1Code;

    @Column(name = "eff1_unit", precision = 10, scale = 6)
    private BigDecimal eff1Unit;

    @Column(name = "eff2_code")
    private Integer eff2Code;

    @Column(name = "eff2_unit", precision = 10, scale = 6)
    private BigDecimal eff2Unit;

    @Column(name = "eff3_code")
    private Integer eff3Code;

    @Column(name = "eff3_unit", precision = 10, scale = 6)
    private BigDecimal eff3Unit;

    @Column(name = "eff4_code")
    private Integer eff4Code;

    @Column(name = "eff4_unit", precision = 10, scale = 6)
    private BigDecimal eff4Unit;

    @Column(name = "eff5_code")
    private Integer eff5Code;

    @Column(name = "eff5_unit", precision = 10, scale = 6)
    private BigDecimal eff5Unit;

    @Column(name = "eff6_code")
    private Integer eff6Code;

    @Column(name = "eff6_unit", precision = 10, scale = 6)
    private BigDecimal eff6Unit;

    @Column(name = "eff7_code")
    private Integer eff7Code;

    @Column(name = "eff7_unit", precision = 10, scale = 6)
    private BigDecimal eff7Unit;

    @Column(name = "eff8_code")
    private Integer eff8Code;

    @Column(name = "eff8_unit", precision = 10, scale = 6)
    private BigDecimal eff8Unit;
}
