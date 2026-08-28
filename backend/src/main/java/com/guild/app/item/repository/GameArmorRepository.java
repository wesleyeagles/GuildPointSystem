package com.guild.app.item.repository;

import com.guild.app.item.dto.GameArmorIconResponse;
import com.guild.app.item.entity.GameArmor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
              AND (
                :filterByCivil = false
                OR g.civilMask IN :civilMasks
              )
              AND (
                :hasSearch = false
                OR LOWER(g.name) LIKE :searchPattern
                OR LOWER(g.gameCode) LIKE :searchPattern
              )
            ORDER BY g.name ASC
            """)
    Page<GameArmor> findFilteredPage(
            @Param("slot") String slot,
            @Param("grade") Integer grade,
            @Param("filterByCivil") boolean filterByCivil,
            @Param("civilMasks") List<String> civilMasks,
            @Param("minLevel") int minLevel,
            @Param("hasSearch") boolean hasSearch,
            @Param("searchPattern") String searchPattern,
            Pageable pageable);

    @Query("""
            SELECT new com.guild.app.item.dto.GameArmorIconResponse(
                g.gameCode, g.name, g.iconId, g.spriteSheet, g.spriteCols
            )
            FROM GameArmor g
            """)
    List<GameArmorIconResponse> findAllIconRefs();
}
