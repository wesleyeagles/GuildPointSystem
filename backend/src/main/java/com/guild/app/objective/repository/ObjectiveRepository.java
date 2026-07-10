package com.guild.app.objective.repository;

import com.guild.app.objective.entity.Objective;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ObjectiveRepository extends JpaRepository<Objective, Long> {

    List<Objective> findByDeletedFalseOrderByCreatedAtDesc();
}
