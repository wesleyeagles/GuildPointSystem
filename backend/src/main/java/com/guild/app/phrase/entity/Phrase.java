package com.guild.app.phrase.entity;

import com.guild.app.member.entity.Member;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Frase marcante de um membro; os membros votam nas preferidas. */
@Entity
@Table(name = "phrase")
@Getter
@Setter
public class Phrase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String text;

    /** Quem falou. Pode faltar nas frases antigas, até o administrador ligar. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    /** Nome como foi anotado; aparece quando não há membro ligado. */
    @Column(name = "said_by", length = 100)
    private String saidBy;

    @Column(name = "said_at")
    private Instant saidAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private Member createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;
}
