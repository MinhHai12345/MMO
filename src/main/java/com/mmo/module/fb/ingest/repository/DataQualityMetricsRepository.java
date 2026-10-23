package com.mmo.module.fb.ingest.repository;

import com.mmo.module.fb.ingest.entity.DataQualityMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DataQualityMetricsRepository extends JpaRepository<DataQualityMetrics, Long> {
}
