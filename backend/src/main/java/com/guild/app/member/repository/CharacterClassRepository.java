package com.guild.app.member.repository;

import com.guild.app.member.entity.CharacterClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CharacterClassRepository extends JpaRepository<CharacterClass, Long> {

    List<CharacterClass> findByRaceIdOrderByNameAsc(Long raceId);
}
