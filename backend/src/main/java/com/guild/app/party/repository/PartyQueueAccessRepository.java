package com.guild.app.party.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class PartyQueueAccessRepository {
    private final JdbcTemplate jdbc;
    public record Override(Boolean open,Instant until) {}

    public Override read(boolean lock) {
        // Entradas compartilham a trava; abrir/fechar aguarda as entradas em andamento.
        return jdbc.queryForObject("SELECT lfg_locked,lfg_override_until FROM party_settings WHERE id=1" + (lock ? " FOR SHARE" : ""),
            (rs,n)->new Override(rs.getTimestamp(2)==null?null:!rs.getBoolean(1),rs.getTimestamp(2)==null?null:rs.getTimestamp(2).toInstant()));
    }
    public void update(boolean open,Instant until,Long actorId) {
        jdbc.update("UPDATE party_settings SET lfg_locked=?,lfg_override_until=?,updated_by_id=?,updated_at=now() WHERE id=1",!open,Timestamp.from(until),actorId);
    }
}
