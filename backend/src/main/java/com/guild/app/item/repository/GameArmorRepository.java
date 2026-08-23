package com.guild.app.item.repository;

import com.guild.app.item.entity.GameArmor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GameArmorRepository extends JpaRepository<GameArmor, Long> {

    Optional<GameArmor> findByGameCode(String gameCode);

    @Query("""
            SELECT g FROM GameArmor g
            WHERE g.levelRequired >= :minLevel
              AND (:slot IS NULL OR g.slot = :slot)
              AND (:grade IS NULL OR g.grade = :grade)
              AND (:civilMask IS NULL OR g.civilMask = :civilMask)
              AND (
                :hasSearch = false
                OR LOWER(g.name) LIKE :searchPattern
                OR LOWER(g.gameCode) LIKE :searchPattern
              )
            ORDER BY g.name ASC
            """)
    List<GameArmor> findFiltered(
            @Param("slot") String slot,
            @Param("grade") Integer grade,
            @Param("civilMask") String civilMask,
            @Param("minLevel") int minLevel,
            @Param("hasSearch") boolean hasSearch,
            @Param("searchPattern") String searchPattern);
}
