package com.guild.app.phrase.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** Um voto de um membro numa frase. */
@Entity
@Table(name = "phrase_vote")
@IdClass(PhraseVote.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class PhraseVote {

    @Id
    @Column(name = "phrase_id")
    private Long phraseId;

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public PhraseVote(Long phraseId, Long memberId) {
        this.phraseId = phraseId;
        this.memberId = memberId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {
        private Long phraseId;
        private Long memberId;

        public Key(Long phraseId, Long memberId) {
            this.phraseId = phraseId;
            this.memberId = memberId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(phraseId, k.phraseId) && Objects.equals(memberId, k.memberId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(phraseId, memberId);
        }
    }
}
