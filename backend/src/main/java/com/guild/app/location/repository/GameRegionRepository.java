package com.guild.app.location.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/** Catalogo de regioes do mini-mapa ja vistas pela DLL (tabela game_region). */
@Repository
@RequiredArgsConstructor
public class GameRegionRepository {

    public record KnownRegion(String map, String region) {}

    private final JdbcTemplate jdbc;

    public void seen(String map, String region, Instant at) {
        jdbc.update("""
                INSERT INTO game_region (map_name, region_name, first_seen_at, last_seen_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (map_name, region_name) DO UPDATE SET last_seen_at = excluded.last_seen_at
                """, map, region, Timestamp.from(at), Timestamp.from(at));
    }

    public List<KnownRegion> findAll() {
        return jdbc.query("SELECT map_name, region_name FROM game_region",
                (rs, i) -> new KnownRegion(rs.getString(1), rs.getString(2)));
    }
}
