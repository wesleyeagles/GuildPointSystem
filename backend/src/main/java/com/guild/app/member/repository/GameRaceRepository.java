package com.guild.app.member.repository;

import com.guild.app.member.entity.GameRace;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRaceRepository extends JpaRepository<GameRace, Long> {
}
