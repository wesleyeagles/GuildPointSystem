package com.guild.app.party.repository;

import com.guild.app.common.enums.PartyPendingKind;
import com.guild.app.common.enums.PartyPendingStatus;
import com.guild.app.party.entity.PartyPending;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PartyPendingRepository extends JpaRepository<PartyPending, Long> {

    List<PartyPending> findByPartyIdAndStatus(Long partyId, PartyPendingStatus status);

    Optional<PartyPending> findByPartyIdAndTargetIdAndStatus(Long partyId, Long targetId, PartyPendingStatus status);

    Optional<PartyPending> findByPartyIdAndInitiatorIdAndKindAndStatus(
            Long partyId, Long initiatorId, PartyPendingKind kind, PartyPendingStatus status);

    @Query("""
            SELECT pp FROM PartyPending pp
            JOIN FETCH pp.party
            JOIN FETCH pp.initiator i
            LEFT JOIN FETCH i.characterClass
            JOIN FETCH pp.target t
            LEFT JOIN FETCH t.characterClass
            WHERE pp.status = :status
            AND (pp.initiator.id = :memberId OR pp.target.id = :memberId)
            """)
    List<PartyPending> findActiveForMember(Long memberId, PartyPendingStatus status);

    @Query("""
            SELECT pp FROM PartyPending pp
            WHERE pp.status = :status AND pp.target.id = :targetId
            """)
    List<PartyPending> findByTargetIdAndStatus(Long targetId, PartyPendingStatus status);
}
