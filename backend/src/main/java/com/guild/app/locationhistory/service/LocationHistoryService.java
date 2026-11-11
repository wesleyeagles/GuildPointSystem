package com.guild.app.locationhistory.service;

import com.guild.app.common.enums.Role;
import com.guild.app.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Histórico de local: cada envio de status da DLL vira uma linha, guardada por {@link #KEEP}. Serve para a staff
 * saber onde alguém estava num horário (ex.: por que não recebeu os Pontos de RG). Consulta só de Administrador+,
 * numa página sem link no site (/onde-estava).
 */
@Service
@RequiredArgsConstructor
public class LocationHistoryService {
    public static final Duration KEEP = Duration.ofDays(3);
    /** Sem membro escolhido: o último envio de cada um até o horário, se tiver no máximo isso de idade. */
    static final Duration LAST_SEEN = Duration.ofMinutes(3);

    private final JdbcTemplate jdbc;

    /** Um envio (chamado pelo CharacterStatusService, na mesma transação). */
    public void record(long memberId, String character, Instant at, String map, String region, Integer x, Integer y,
                       Instant idleSince, String dllVersion) {
        jdbc.update("""
                INSERT INTO location_history(member_id,character_name,at,map_name,region_name,map_x,map_y,idle_since,dll_version)
                VALUES(?,?,?,?,?,?,?,?,?)""",
                memberId, cut(character, 32), Timestamp.from(at), cut(map, 64), cut(region, 64), x, y,
                idleSince == null ? null : Timestamp.from(idleSince), cut(dllVersion, 16));
    }

    /** idleSeconds = parado sem EXP há quanto tempo naquele envio (null = sem coordenada). */
    public record Row(long memberId, String nickname, String character, Instant at, String map, String region,
                      Integer x, Integer y, Long idleSeconds, String dllVersion) {}

    /**
     * Com {@code nickname}: todos os envios dos membros com esse nick (sem diferença de maiúscula, pedaço do nome
     * vale) de {@code minutes} antes a {@code minutes} depois do horário. Sem nick: onde cada membro estava no
     * horário (último envio até ele, de no máximo 3 min antes).
     */
    @Transactional(readOnly = true)
    public List<Row> query(Instant at, String nickname, int minutes) {
        SecurityUtils.requireRole(Role.ADMINISTRADOR);
        if (nickname != null && !nickname.isBlank()) {
            Duration span = Duration.ofMinutes(Math.max(1, Math.min(minutes, 180)));
            return jdbc.query("SELECT " + COLUMNS + FROM + " WHERE m.nickname ILIKE ? AND h.at BETWEEN ? AND ? ORDER BY m.nickname, h.at",
                    (rs, n) -> row(rs), "%" + nickname.strip().replace("%", "") + "%",
                    Timestamp.from(at.minus(span)), Timestamp.from(at.plus(span)));
        }
        return jdbc.query("SELECT * FROM (SELECT DISTINCT ON (h.member_id) " + COLUMNS + FROM
                        + " WHERE h.at BETWEEN ? AND ? ORDER BY h.member_id, h.at DESC) x ORDER BY lower(nickname)",
                (rs, n) -> row(rs), Timestamp.from(at.minus(LAST_SEEN)), Timestamp.from(at));
    }

    private static final String COLUMNS = """
            h.member_id, m.nickname, h.character_name, h.at, h.map_name, h.region_name, h.map_x, h.map_y,
            h.idle_since, h.dll_version""";
    private static final String FROM = " FROM location_history h JOIN member m ON m.id = h.member_id";

    /** Apaga o que passou de 3 dias (LocationHistoryScheduler, de hora em hora). */
    @Transactional
    public int cleanup(Instant now) {
        return jdbc.update("DELETE FROM location_history WHERE at < ?", Timestamp.from(now.minus(KEEP)));
    }

    private static Row row(java.sql.ResultSet rs) throws java.sql.SQLException {
        Instant at = rs.getTimestamp("at").toInstant();
        Timestamp idle = rs.getTimestamp("idle_since");
        return new Row(rs.getLong("member_id"), rs.getString("nickname"), rs.getString("character_name"), at,
                rs.getString("map_name"), rs.getString("region_name"),
                (Integer) rs.getObject("map_x"), (Integer) rs.getObject("map_y"),
                idle == null ? null : Math.max(0, Duration.between(idle.toInstant(), at).getSeconds()),
                rs.getString("dll_version"));
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        String t = s.strip();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }
}
