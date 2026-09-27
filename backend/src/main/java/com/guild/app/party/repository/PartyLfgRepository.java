package com.guild.app.party.repository;

import com.guild.app.party.entity.PartyLfg;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PartyLfgRepository extends JpaRepository<PartyLfg, Long> {

    List<PartyLfg> findAllByOrderByCreatedAtAsc();

    Optional<PartyLfg> findByMemberId(Long memberId);

    @Query("""
            SELECT l FROM PartyLfg l
            JOIN FETCH l.member m
            LEFT JOIN FETCH m.characterClass
            ORDER BY l.createdAt ASC
            """)
    List<PartyLfg> findAllWithMemberOrderByCreatedAtAsc();
}
