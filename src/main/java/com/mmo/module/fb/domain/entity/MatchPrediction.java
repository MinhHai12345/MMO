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
@Table(name = "fb_match_predictions",
        indexes = {
                @Index(name = "idx_fb_prediction_status_calculated", columnList = "prediction_status, calculated_at"),
                @Index(name = "idx_fb_prediction_premium", columnList = "is_premium")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchPrediction extends AbstractEntity {

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Column(name = "predicted_home_xg", precision = 6, scale = 3)
    private BigDecimal predictedHomeXg;

    @Column(name = "predicted_away_xg", precision = 6, scale = 3)
    private BigDecimal predictedAwayXg;

    @Column(name = "win_home_prob", precision = 9, scale = 6)
    private BigDecimal winHomeProb;

    @Column(name = "draw_prob", precision = 9, scale = 6)
    private BigDecimal drawProb;

    @Column(name = "win_away_prob", precision = 9, scale = 6)
    private BigDecimal winAwayProb;

    @Column
    private String recommendedPick;

    @Column(name = "predicted_result", length = 16)
    private String predictedResult;

    @Column(name = "prediction_status", nullable = false, length = 32)
    @Builder.Default
    private String predictionStatus = "PENDING";

    @Column(name = "is_premium")
    @Builder.Default
    private Boolean isPremium = false;

    @Column(name = "value_bet_type", length = 32)
    private String valueBetType;

    @Column(name = "confidence_score", precision = 5, scale = 2)
    private BigDecimal confidenceScore;

    @Column(name = "calculated_at", nullable = false)
    @Builder.Default
    private Instant calculatedAt = Instant.now();

    @Column(columnDefinition = "TEXT")
    private String predictionReasoning;

    @Column(columnDefinition = "TEXT")
    private String telegramPostHtml;

    @Column(columnDefinition = "TEXT")
    private String facebookPostText;

    @Column(columnDefinition = "TEXT")
    private String twitterThreadJson;

    @Column
    private String imageUrl;

    @OneToOne(mappedBy = "prediction", cascade = CascadeType.ALL, orphanRemoval = true)
    private PredictionPerformance performance;

}
