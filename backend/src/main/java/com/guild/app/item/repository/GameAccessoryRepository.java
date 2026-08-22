package com.guild.app.item.repository;

import com.guild.app.item.entity.GameAccessory;
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
              AND (:civilMask IS NULL OR g.civilMask = :civilMask)
              AND (:search IS NULL OR LOWER(g.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(g.gameCode) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY g.name ASC
            """)
    List<GameAccessory> findFiltered(
            @Param("subtype") String subtype,
            @Param("grade") Integer grade,
            @Param("civilMask") String civilMask,
            @Param("search") String search);
}
