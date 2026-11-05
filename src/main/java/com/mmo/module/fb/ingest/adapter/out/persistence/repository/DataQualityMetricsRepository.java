package com.mmo.module.fb.ingest.adapter.out.persistence.repository;

import com.mmo.module.fb.ingest.adapter.out.persistence.entity.DataQualityMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DataQualityMetricsRepository extends JpaRepository<DataQualityMetrics, Long> {
}
