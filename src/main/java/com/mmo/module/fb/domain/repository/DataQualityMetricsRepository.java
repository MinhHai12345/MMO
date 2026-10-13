package com.mmo.module.fb.domain.repository;

import com.mmo.module.fb.domain.entity.DataQualityMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DataQualityMetricsRepository extends JpaRepository<DataQualityMetrics, Long> {
}
