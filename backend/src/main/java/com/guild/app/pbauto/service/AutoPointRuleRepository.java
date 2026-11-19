package com.guild.app.pbauto.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalTime;
import java.util.*;

/** Tabela auto_point_rule. Regiões guardadas uma por linha. */
@Repository
@RequiredArgsConstructor
public class AutoPointRuleRepository {
    private final JdbcTemplate jdbc;

    public List<AutoPointRule> findAll() {
        return jdbc.query("SELECT * FROM auto_point_rule ORDER BY COALESCE(rg_at, presence_from), id", (rs, i) -> map(rs));
    }

    public Optional<AutoPointRule> findById(long id) {
        return jdbc.query("SELECT * FROM auto_point_rule WHERE id=?", (rs, i) -> map(rs), id).stream().findFirst();
    }

    public long insert(AutoPointRule r) {
        Long id = jdbc.queryForObject("INSERT INTO auto_point_rule(code,label) VALUES(?,?) RETURNING id", Long.class,
                UUID.randomUUID().toString().replace("-", ""), r.label());
        jdbc.update("UPDATE auto_point_rule SET code=? WHERE id=?", "R" + id, id);
        update(id, r);
        return id;
    }

    public void update(long id, AutoPointRule r) {
        var rg = r.rg();
        var p = r.presence();
        jdbc.update("""
                UPDATE auto_point_rule SET label=?, enabled=?, weekdays=?, shouts_before=?, shout=?, shout_en=?,
                    rg_enabled=?, rg_at=?, rg_map=?, rg_regions=?, rg_points=?,
                    presence_enabled=?, presence_from=?, presence_to=?, presence_map=?, presence_regions=?, objective_name=?,
                    updated_at=now()
                WHERE id=?""",
                r.label(), r.enabled(), r.weekdays(),
                String.join(",", r.shoutsBefore().stream().map(String::valueOf).toList()), r.shout(), r.shoutEn(),
                rg != null, rg != null ? Time.valueOf(rg.at()) : null, rg != null ? rg.map() : null,
                rg != null ? String.join("\n", rg.regions()) : "", rg != null ? rg.points() : 0,
                p != null, p != null ? Time.valueOf(p.from()) : null, p != null ? Time.valueOf(p.to()) : null,
                p != null ? p.map() : null, p != null ? String.join("\n", p.regions()) : "", p != null ? p.objective() : null,
                id);
    }

    public boolean delete(long id) { return jdbc.update("DELETE FROM auto_point_rule WHERE id=?", id) > 0; }

    private static AutoPointRule map(ResultSet rs) throws SQLException {
        AutoPointRule.Rg rg = rs.getBoolean("rg_enabled") && rs.getTime("rg_at") != null
                ? new AutoPointRule.Rg(time(rs, "rg_at"), rs.getString("rg_map"), lines(rs.getString("rg_regions")),
                        rs.getInt("rg_points"))
                : null;
        AutoPointRule.Presence presence = rs.getBoolean("presence_enabled") && rs.getTime("presence_from") != null
                && rs.getTime("presence_to") != null
                ? new AutoPointRule.Presence(time(rs, "presence_from"), time(rs, "presence_to"), rs.getString("presence_map"),
                        lines(rs.getString("presence_regions")), rs.getString("objective_name"))
                : null;
        List<Integer> shouts = Arrays.stream(Objects.toString(rs.getString("shouts_before"), "").split(","))
                .map(String::strip).filter(s -> !s.isEmpty()).map(Integer::valueOf).toList();
        return new AutoPointRule(rs.getLong("id"), rs.getString("code"), rs.getString("label"), rs.getBoolean("enabled"),
                rs.getInt("weekdays"), shouts, rs.getString("shout"), rs.getString("shout_en"), rg, presence);
    }

    private static LocalTime time(ResultSet rs, String column) throws SQLException {
        return rs.getTime(column).toLocalTime();
    }

    static List<String> lines(String text) {
        return Arrays.stream(Objects.toString(text, "").split("\n")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
}
