package com.guild.app.quest.repository;

import com.guild.app.quest.dto.BdCompletionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class BdCompletionRepository {
    private final JdbcTemplate jdbc;

    public void record(BdCompletionRequest event, LocalDate day, Instant observed) {
        jdbc.update("""
                INSERT INTO bd_daily_completion(character_key, quest_code, day, observed_at,
                  previous_slot, completed_slot, definition_hash) VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (character_key, quest_code, day) DO NOTHING
                """, event.character().strip().toLowerCase(Locale.ROOT), event.code(), day,
                Timestamp.from(observed), event.previousSlot(), event.completedSlot(), event.definitionHash());
    }

    public Map<String, Instant> forDay(LocalDate day) {
        Map<String, Instant> result = new HashMap<>();
        jdbc.query("SELECT character_key, observed_at FROM bd_daily_completion WHERE day = ?",
                (org.springframework.jdbc.core.RowCallbackHandler) row -> result.put(row.getString(1), row.getTimestamp(2).toInstant()), day);
        return result;
    }
}
