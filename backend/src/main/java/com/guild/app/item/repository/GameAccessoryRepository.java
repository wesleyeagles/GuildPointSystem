package com.guild.app.item.repository;

import com.guild.app.item.dto.GameAccessoryIconResponse;
import com.guild.app.item.entity.GameAccessory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GameAccessoryRepository extends JpaRepository<GameAccessory, Long> {

    Optional<GameAccessory> findByGameCode(String gameCode);

    @Query("""
            SELECT g FROM GameAccessory g
            WHERE (:subtype IS NULL OR g.subtype = :subtype)
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
    Page<GameAccessory> findFilteredPage(
            @Param("subtype") String subtype,
            @Param("grade") Integer grade,
            @Param("filterByCivil") boolean filterByCivil,
            @Param("civilMasks") List<String> civilMasks,
            @Param("hasSearch") boolean hasSearch,
            @Param("searchPattern") String searchPattern,
            Pageable pageable);

    @Query("""
            SELECT new com.guild.app.item.dto.GameAccessoryIconResponse(
                g.gameCode, g.name, g.iconId, g.spriteSheet
            )
            FROM GameAccessory g
            """)
    List<GameAccessoryIconResponse> findAllIconRefs();
}
