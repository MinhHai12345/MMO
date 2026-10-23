package com.mmo.module.fb.analytics.repository;

import com.mmo.module.fb.analytics.entity.PredictionPerformance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PredictionPerformanceRepository extends JpaRepository<PredictionPerformance, Long> {
}
