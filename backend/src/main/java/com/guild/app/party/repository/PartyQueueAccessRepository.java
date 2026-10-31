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
        return jdbc.queryForObject("SELECT override_open,override_until FROM party_queue_access WHERE id=1" + (lock ? " FOR SHARE" : ""),
            (rs,n)->new Override(rs.getObject(1,Boolean.class),rs.getTimestamp(2)==null?null:rs.getTimestamp(2).toInstant()));
    }
    public void update(boolean open,Instant until) {
        jdbc.update("UPDATE party_queue_access SET override_open=?,override_until=? WHERE id=1",open,Timestamp.from(until));
    }
}
