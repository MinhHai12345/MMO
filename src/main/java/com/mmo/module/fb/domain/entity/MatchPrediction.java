package com.mmo.module.fb.domain.entity;

import com.mmo.core.entity.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

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
    private Double predictedHomeXg;

    @Column(precision = 5, scale = 2)
    private Double predictedAwayXg;

    @Column(precision = 5, scale = 2)
    private Double winHomeProb;

    @Column(precision = 5, scale = 2)
    private Double drawProb;

    @Column(precision = 5, scale = 2)
    private Double winAwayProb;

    @Column
    private String recommendedPick;

    @Column(precision = 5, scale = 2)
    private Double confidenceScore;

    @Column(columnDefinition = "TEXT")
    private String predictionReasoning;

    @Column
    private Instant calculatedAt;

    @Column(columnDefinition = "TEXT")
    private String telegramPostHtml;

    @Column(columnDefinition = "TEXT")
    private String facebookPostText;

    @Column(columnDefinition = "TEXT")
    private String twitterThreadJson;

    @Column
    private String imageUrl;
}
