package com.mmo.module.fb.core.model;

import com.mmo.module.fb.enums.ValueBetType;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter
@Builder
@ToString
public class PredictionResult {
    // Expected Goals (Lambda)
    private double expectedHomeXG;
    private double expectedAwayXG;
    private double h2TotalXG;
    private String handicapMargin;

    // Dixon-Coles Probabilities (Range: 0.0 - 1.0)
    private double homeWinProb;
    private double drawProb;
    private double awayWinProb;

    // Fair Odds (1 / Prob)
    private double fairHomeOdd;
    private double fairDrawOdd;
    private double fairAwayOdd;

    // Goal Markets
    private double probUnder25;
    private double probOver25;
    private List<ScoreProbability> topCorrectScores;

    // Implied Market Metrics
    private double marketHomeXG;
    private double marketAwayXG;

    // Value Bet & Staking
    private boolean hasValue;
    private boolean isPremium;
    private ValueBetType valueBetType;
    private double edgePercentage;
    private double smartStakingSize;
}
