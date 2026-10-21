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
@Table(name = "fb_data_quality_metrics",
        indexes = {
                @Index(name = "idx_fb_quality_type_measured", columnList = "metric_type, measured_at"),
                @Index(name = "idx_fb_quality_status", columnList = "status")
        })
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DataQualityMetrics extends AbstractEntity {

    @Column(name = "metric_type", nullable = false, length = 100)
    private String metricType;

    @Column(name = "metric_name", nullable = false, length = 160)
    private String metricName;

    @Column(name = "metric_value", precision = 16, scale = 4)
    private BigDecimal metricValue;

    @Column(precision = 16, scale = 4)
    private BigDecimal threshold;

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String status = "OK";

    @Column(columnDefinition = "JSON")
    private String details;

    @Column(name = "measured_at")
    private Instant measuredAt;
}
