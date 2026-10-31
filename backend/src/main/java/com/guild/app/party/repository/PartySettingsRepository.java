package com.guild.app.party.repository;

import com.guild.app.party.entity.PartySettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartySettingsRepository extends JpaRepository<PartySettings, Short> {
}
