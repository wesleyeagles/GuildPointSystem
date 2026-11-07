package com.guild.app.phrase.repository;

import com.guild.app.phrase.entity.PhraseVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PhraseVoteRepository extends JpaRepository<PhraseVote, PhraseVote.Key> {

    long countByMemberId(Long memberId);

    boolean existsByPhraseIdAndMemberId(Long phraseId, Long memberId);

    long deleteByPhraseIdAndMemberId(Long phraseId, Long memberId);

    /** Todos os votos com o nick de quem votou: [phraseId, memberId, nickname], do mais antigo ao mais novo. */
    @Query("""
            SELECT v.phraseId, v.memberId, m.nickname
            FROM PhraseVote v, Member m
            WHERE m.id = v.memberId
            ORDER BY v.createdAt, v.memberId
            """)
    List<Object[]> findAllWithNickname();
}
