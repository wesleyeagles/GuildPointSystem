package com.guild.app.party.repository;

import com.guild.app.common.enums.PartyMap;
import com.guild.app.party.entity.Party;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PartyRepository extends JpaRepository<Party, Long> {

    List<Party> findByMapOrderByCreatedAtAsc(PartyMap map);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Party p WHERE p.id = :id")
    Optional<Party> findByIdForUpdate(Long id);
}
