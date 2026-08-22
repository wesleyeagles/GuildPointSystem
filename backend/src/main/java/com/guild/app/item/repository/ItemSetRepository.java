package com.guild.app.item.repository;

import com.guild.app.item.entity.ItemSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemSetRepository extends JpaRepository<ItemSet, Long> {

    @Query("""
            SELECT s FROM ItemSet s
            WHERE s.head = :gameCode OR s.upper = :gameCode OR s.lower = :gameCode
               OR s.shoes = :gameCode OR s.gauntlet = :gameCode OR s.weapon = :gameCode
               OR s.shield = :gameCode OR s.amul1 = :gameCode OR s.amul2 = :gameCode
               OR s.ring1 = :gameCode OR s.ring2 = :gameCode OR s.cloack = :gameCode
            """)
    List<ItemSet> findByGameCode(@Param("gameCode") String gameCode);
}
