package com.guild.app.event.repository;

import com.guild.app.event.entity.GuildEvent;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface GuildEventRepository extends JpaRepository<GuildEvent, Long> {

    @EntityGraph(attributePaths = "objective")
    List<GuildEvent> findByActiveTrueAndExpiresAtAfterOrderByCreatedAtDesc(Instant now);
}
