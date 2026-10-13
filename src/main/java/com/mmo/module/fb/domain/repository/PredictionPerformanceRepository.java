package com.mmo.module.fb.domain.repository;

import com.mmo.module.fb.domain.entity.PredictionPerformance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PredictionPerformanceRepository extends JpaRepository<PredictionPerformance, Long> {
}
