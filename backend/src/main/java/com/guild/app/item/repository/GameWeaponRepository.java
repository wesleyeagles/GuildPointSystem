package com.guild.app.item.repository;

import com.guild.app.item.dto.GameWeaponIconResponse;
import com.guild.app.item.entity.GameWeapon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GameWeaponRepository extends JpaRepository<GameWeapon, Long> {

    Optional<GameWeapon> findByGameCode(String gameCode);

    @Query("""
            SELECT g FROM GameWeapon g
            WHERE g.levelRequired >= :minLevel
              AND (:weaponType IS NULL OR g.weaponType = :weaponType)
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
    Page<GameWeapon> findFilteredPage(
            @Param("weaponType") String weaponType,
            @Param("grade") Integer grade,
            @Param("filterByCivil") boolean filterByCivil,
            @Param("civilMasks") List<String> civilMasks,
            @Param("minLevel") int minLevel,
            @Param("hasSearch") boolean hasSearch,
            @Param("searchPattern") String searchPattern,
            Pageable pageable);

    @Query("""
            SELECT new com.guild.app.item.dto.GameWeaponIconResponse(
                g.gameCode, g.name, g.iconId, g.spriteSheet, g.spriteCols
            )
            FROM GameWeapon g
            """)
    List<GameWeaponIconResponse> findAllIconRefs();
}
