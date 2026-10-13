package com.mmo.module.fb.domain.entity;

import com.mmo.core.entity.AbstractEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "fb_data_quality_metrics")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DataQualityMetrics extends AbstractEntity {

    @Column(name = "metric_type", nullable = false)
    private String metricType;

    @Column(name = "metric_name", nullable = false)
    private String metricName;

    @Column(name = "metric_value")
    private BigDecimal metricValue;

    private BigDecimal threshold;

    private String status = "OK";

    @Column(columnDefinition = "JSON")
    private String details;

    @Column(name = "measured_at")
    private Instant measuredAt;
}
