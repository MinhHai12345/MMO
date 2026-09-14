package com.mmo.module.fb.entity;

import com.mmo.entity.AbstractEntity;
import com.mmo.module.fb.entity.enums.MatchPredictionStatus;
import com.mmo.module.fb.entity.enums.ValueBetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "match_predictions",
        indexes = {
                @Index(name = "idx_match_id", columnList = "match_id", unique = true),
                @Index(name = "idx_status_has_value", columnList = "status, has_value"),
                @Index(name = "idx_premium_distribution", columnList = "is_premium, is_posted_social")
        })
@Getter
@Setter
public class MatchPrediction extends AbstractEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    // =========================================================================
    // 📊 MODEL EXPECTED METRICS (Kết quả tính toán nội bộ Engine)
    // =========================================================================
    @Column(nullable = false, precision = 4, scale = 2)
    private Double expectedHomeXG;

    @Column(nullable = false, precision = 4, scale = 2)
    private Double expectedAwayXG;

    @Column(nullable = false, precision = 4, scale = 2)
    private Double h2TotalXG;

    @Column(length = 15)
    private String handicapMargin;

    // =========================================================================
    // 🎯 MODEL PROBABILITIES & FAIR ODDS (Đã tính qua Dixon-Coles)
    // =========================================================================
    @Column(nullable = false, precision = 5, scale = 4) // 0.4525 (45.25%)
    private Double homeWinProb;

    @Column(nullable = false, precision = 5, scale = 4)
    private Double drawProb;

    @Column(nullable = false, precision = 5, scale = 4)
    private Double awayWinProb;

    @Column(precision = 6, scale = 2)
    private Double fairHomeOdd;

    @Column(precision = 6, scale = 2)
    private Double fairDrawOdd;

    @Column(precision = 6, scale = 2)
    private Double fairAwayOdd;

    @Column(precision = 5, scale = 2)
    private Double probUnder25;

    @Column(precision = 5, scale = 2)
    private Double probOver25;

    @Column(length = 50)
    private String topCorrectScores;

    // =========================================================================
    // 🏛️ MARKET ODDS & IMPLIED XG (Dữ liệu thị trường từ Nhà cái)
    // =========================================================================
    @Column(precision = 6, scale = 2)
    private Double marketHomeOdd;

    @Column(precision = 6, scale = 2)
    private Double marketDrawOdd;

    @Column(precision = 6, scale = 2)
    private Double marketAwayOdd;

    @Column(precision = 4, scale = 2)
    private Double marketHomeXG;

    @Column(precision = 4, scale = 2)
    private Double marketAwayXG;

    // =========================================================================
    // 💰 VALUE BET & KELLY STAKING ANALYSIS
    // =========================================================================
    @Column(nullable = false)
    private boolean hasValue = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ValueBetType valueBetType;

    @Column(precision = 5, scale = 2)
    private Double edgePercentage;

    @Column(precision = 3, scale = 1)
    private Double smartStakingSize;

    // =========================================================================
    // 🚀 BUSINESS, E-COMMERCE & SOCIAL DISTRIBUTION STATE
    // =========================================================================
    @Column(nullable = false)
    private boolean isPremium = false;

    @Column(nullable = false)
    private boolean isPostedSocial = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 25, nullable = false)
    private MatchPredictionStatus status = MatchPredictionStatus.PENDING;
}
