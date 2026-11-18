package com.guild.app.party.repository;

import com.guild.app.party.entity.PartySettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartySettingsRepository extends JpaRepository<PartySettings, Short> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PartySettings s SET s.notice = :notice WHERE s.id = 1")
    int updateNotice(@Param("notice") String notice);
}
