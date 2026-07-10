package com.guild.app.points.repository;

import com.guild.app.points.entity.PointsAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointsAdjustmentRepository extends JpaRepository<PointsAdjustment, Long> {
}
