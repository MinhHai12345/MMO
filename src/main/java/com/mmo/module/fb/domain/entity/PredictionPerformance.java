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
        indexes = @Index(name = "idx_fb_prediction_performance_correct", columnList = "is_correct"))
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PredictionPerformance extends AbstractEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prediction_id", nullable = false, unique = true)
    private MatchPrediction prediction;

    @Column(name = "actual_home_score")
    private Integer actualHomeScore;

    @Column(name = "actual_away_score")
    private Integer actualAwayScore;

    @Column(name = "actual_result", length = 16)
    private String actualResult;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "accuracy_score", precision = 7, scale = 6)
    private BigDecimal accuracyScore;

    @Column(name = "evaluated_at")
    private Instant evaluatedAt;
}
