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
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "fb_match_predictions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchPrediction extends AbstractEntity {

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    private Match match;

    @Column(precision = 5, scale = 2)
    private BigDecimal predictedHomeXg;

    @Column(precision = 5, scale = 2)
    private BigDecimal predictedAwayXg;

    @Column(precision = 5, scale = 2)
    private BigDecimal winHomeProb;

    @Column(precision = 5, scale = 2)
    private BigDecimal drawProb;

    @Column(precision = 5, scale = 2)
    private BigDecimal winAwayProb;

    @Column
    private String recommendedPick;

    @Column(name = "predicted_result")
    private String predictedResult;

    @Column(name = "prediction_status")
    @Builder.Default
    private String predictionStatus = "PENDING";

    @Column
    private String result;

    @Column(name = "is_premium")
    @Builder.Default
    private Boolean isPremium = false;

    @Column(name = "value_bet_type")
    private String valueBetType;

    @Column(precision = 5, scale = 2)
    private BigDecimal confidenceScore;

    @Column
    private Instant calculatedAt;

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

    @OneToMany(mappedBy = "prediction", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PredictionPerformance> performance = new ArrayList<>();

}
