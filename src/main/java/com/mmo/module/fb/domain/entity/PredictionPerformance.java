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
@Table(name = "fb_prediction_performance",
        uniqueConstraints = @UniqueConstraint(name = "uk_fb_prediction_performance_prediction", columnNames = "prediction_id"),
        indexes = @Index(name = "idx_fb_prediction_performance_correct", columnList = "is_correct"))
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PredictionPerformance extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prediction_id", nullable = false)
    private MatchPrediction prediction;

    @Column(name = "actual_home_score")
    private Integer actualHomeScore;

    @Column(name = "actual_away_score")
    private Integer actualAwayScore;

    @Column(name = "actual_result")
    private String actualResult;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "accuracy_score")
    private BigDecimal accuracyScore;

    @Column(name = "evaluated_at")
    private Instant evaluatedAt;
}
