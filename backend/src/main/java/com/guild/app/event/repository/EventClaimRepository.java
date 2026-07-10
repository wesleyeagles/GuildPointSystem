package com.guild.app.event.repository;

import com.guild.app.event.entity.EventClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EventClaimRepository extends JpaRepository<EventClaim, Long> {

    boolean existsByEventIdAndMemberId(Long eventId, Long memberId);

    List<EventClaim> findTop20ByMemberIdOrderByClaimedAtDesc(Long memberId);

    @Query(value = """
            SELECT COUNT(*) FROM event_claim c
            WHERE c.member_id = :memberId AND c.objective_id = :objectiveId
            AND c.denied = false AND c.claimed_date = CURRENT_DATE
            """, nativeQuery = true)
    long countDailyClaims(Long memberId, Long objectiveId);
}
