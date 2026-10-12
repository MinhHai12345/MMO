package com.mmo.module.fb.core.engine;

import com.mmo.module.fb.core.model.PredictionResult;
import com.mmo.module.fb.core.model.ScoreProbability;
import com.mmo.module.fb.provider.model.sofa.SofaMatchesData;
import com.mmo.module.fb.enums.ValueBetType;
import org.apache.commons.math3.distribution.PoissonDistribution;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class PoissonCalculatorEngine {

    private static final int MAX_GOALS = 9;
    private static final double MIN_EDGE_FREE = 5.0;
    private static final double MIN_EDGE_PREMIUM = 10.0;
    private static final double MAX_RELIABLE_EDGE = 50.0;
    private static final double MAX_RANK_MODIFIER = 0.15;
    private static final double ABSOLUTE_MIN_LAMBDA = 0.2;
    private static final double DIXON_COLES_RHO = -0.13; // Correlation parameter for low scores

    /**
     * Entry Point: Tính toán toàn bộ chỉ số cho trận đấu
     */
    public PredictionResult calculate(List<SofaMatchesData.SofaEventDTO> homeHistories, List<SofaMatchesData.SofaEventDTO> awayHistories,
                                      Long homeTeamId, Long awayTeamId, Integer homeRank, Integer awayRank, Double sofaHomeOdd,
                                      Double sofaDrawOdd, Double sofaAwayOdd) {

        // Step 1: Weighted Recency Average
        double[] homeStats = calculateWeightedAverages(homeHistories, homeTeamId, true);
        double[] awayStats = calculateWeightedAverages(awayHistories, awayTeamId, false);

        // Step 2: Strength & Rank Modifiers
        double avgTournamentScored = 1.35;
        double homeAttack = homeStats[0] / avgTournamentScored;
        double homeDefense = homeStats[1] / avgTournamentScored;
        double awayAttack = awayStats[0] / avgTournamentScored;
        double awayDefense = awayStats[1] / avgTournamentScored;

        if (homeRank != null && awayRank != null && homeRank > 0 && awayRank > 0) {
            int rankDiff = awayRank - homeRank;
            double rankModifier = Math.max(-MAX_RANK_MODIFIER, Math.min(MAX_RANK_MODIFIER, rankDiff * 0.01));
            homeAttack *= (1.0 + rankModifier);
            homeDefense *= (1.0 - rankModifier);
            awayAttack *= (1.0 - rankModifier);
            awayDefense *= (1.0 + rankModifier);
        }

        // Step 3: Expected Goals (Lambda)
        double lambdaHome = Math.max(ABSOLUTE_MIN_LAMBDA, homeAttack * awayDefense * avgTournamentScored);
        double lambdaAway = Math.max(ABSOLUTE_MIN_LAMBDA, awayAttack * homeDefense * avgTournamentScored);

        // Step 4: Dixon-Coles Probability Matrix
        double[][] matrix = buildDixonColesMatrix(lambdaHome, lambdaAway);

        double homeWinProb = 0.0;
        double drawProb = 0.0;
        double awayWinProb = 0.0;
        double probUnder25 = 0.0;

        List<ScoreProbability> scoreList = new ArrayList<>();

        for (int h = 0; h < MAX_GOALS; h++) {
            for (int a = 0; a < MAX_GOALS; a++) {
                double prob = matrix[h][a];
                scoreList.add(new ScoreProbability(h + "-" + a, prob));

                if ((h + a) < 2.5) probUnder25 += prob;
                if (h > a) homeWinProb += prob;
                else if (h == a) drawProb += prob;
                else awayWinProb += prob;
            }
        }

        Collections.sort(scoreList);
        List<ScoreProbability> topCorrectScores = scoreList.stream().limit(3).toList();

        // Step 5: Market Implied xG
        double[] marketXG = calculateMarketXG(sofaHomeOdd, sofaDrawOdd, sofaAwayOdd);

        // Step 6: Value Bet & Kelly Staking
        ValueBetEvaluation valueEval = evaluateValueBet(homeWinProb, drawProb, awayWinProb, sofaHomeOdd, sofaDrawOdd, sofaAwayOdd);

        return PredictionResult.builder()
                .expectedHomeXG(round(lambdaHome))
                .expectedAwayXG(round(lambdaAway))
                .h2TotalXG(round(lambdaHome + lambdaAway))
                .handicapMargin(calculateHandicapMargin(lambdaHome, lambdaAway))
                .homeWinProb(round4(homeWinProb))
                .drawProb(round4(drawProb))
                .awayWinProb(round4(awayWinProb))
                .fairHomeOdd(homeWinProb > 0 ? round(1.0 / homeWinProb) : 99.0)
                .fairDrawOdd(drawProb > 0 ? round(1.0 / drawProb) : 99.0)
                .fairAwayOdd(awayWinProb > 0 ? round(1.0 / awayWinProb) : 99.0)
                .probUnder25(round(probUnder25 * 100))
                .probOver25(round((1.0 - probUnder25) * 100))
                .topCorrectScores(topCorrectScores)
                .marketHomeXG(marketXG[0])
                .marketAwayXG(marketXG[1])
                .hasValue(valueEval.hasValue)
                .isPremium(valueEval.isPremium)
                .valueBetType(valueEval.type)
                .edgePercentage(round(valueEval.edgePercentage))
                .smartStakingSize(valueEval.stakingSize)
                .build();
    }

    /**
     * Fix Bug Recency: Sắp xếp thứ tự trận đấu từ mới nhất đến cũ nhất
     */
    private double[] calculateWeightedAverages(List<SofaMatchesData.SofaEventDTO> histories, Long teamId, boolean isHomePosition) {
        double totalWeightedScored = 0.0;
        double totalWeightedConceded = 0.0;
        double totalWeight = 0.0;

        int totalMatches = histories.size();

        for (int i = 0; i < totalMatches; i++) {
            SofaMatchesData.SofaEventDTO match = histories.get(i);
            if (match == null || match.getStatus() == null || !"finished".equalsIgnoreCase(match.getStatus().getType()))
                continue;

            boolean isMatchHome = teamId.equals(match.getHomeTeam().getId());

            // Index đảo ngược: recencyIndex = 0 là trận vừa diễn ra gần nhất
            int recencyIndex = totalMatches - 1 - i;

            double recencyWeight = (recencyIndex < 5) ? 3.0 : ((recencyIndex < 12) ? 2.0 : 1.0);
            double positionWeight = (isHomePosition == isMatchHome) ? 1.5 : 1.0;
            double finalWeight = recencyWeight * positionWeight;

            double scored = isMatchHome ? match.getHomeScore().getCurrent() : match.getAwayScore().getCurrent();
            double conceded = isMatchHome ? match.getAwayScore().getCurrent() : match.getHomeScore().getCurrent();

            totalWeightedScored += (scored * finalWeight);
            totalWeightedConceded += (conceded * finalWeight);
            totalWeight += finalWeight;
        }

        if (totalWeight == 0) return new double[]{1.2, 1.2};
        return new double[]{totalWeightedScored / totalWeight, totalWeightedConceded / totalWeight};
    }

    /**
     * Ma trận Dixon-Coles điều chỉnh hệ số Tau
     */
    private double[][] buildDixonColesMatrix(double lambdaHome, double lambdaAway) {
        PoissonDistribution homeDist = new PoissonDistribution(lambdaHome);
        PoissonDistribution awayDist = new PoissonDistribution(lambdaAway);

        double[][] matrix = new double[MAX_GOALS][MAX_GOALS];

        for (int h = 0; h < MAX_GOALS; h++) {
            for (int a = 0; a < MAX_GOALS; a++) {
                double pHome = homeDist.probability(h);
                double pAway = awayDist.probability(a);
                double tau = calculateTau(h, a, lambdaHome, lambdaAway, DIXON_COLES_RHO);
                matrix[h][a] = pHome * pAway * tau;
            }
        }
        return matrix;
    }

    private double calculateTau(int h, int a, double lambda, double mu, double rho) {
        if (h == 0 && a == 0) return 1.0 - (lambda * mu * rho);
        if (h == 0 && a == 1) return 1.0 + (lambda * rho);
        if (h == 1 && a == 0) return 1.0 + (mu * rho);
        if (h == 1 && a == 1) return 1.0 - rho;
        return 1.0;
    }

    /**
     * Bẻ ngược tỷ lệ Nhà cái loại bỏ Margin
     */
    private double[] calculateMarketXG(Double homeOdd, Double drawOdd, Double awayOdd) {
        if (homeOdd == null || drawOdd == null || awayOdd == null || homeOdd <= 1.0) {
            return new double[]{0.0, 0.0};
        }

        double rawHomeProb = 1.0 / homeOdd;
        double rawDrawProb = 1.0 / drawOdd;
        double rawAwayProb = 1.0 / awayOdd;
        double totalMargin = rawHomeProb + rawDrawProb + rawAwayProb;

        double homeProb = rawHomeProb / totalMargin;
        double drawProb = rawDrawProb / totalMargin;
        double awayProb = rawAwayProb / totalMargin;

        double totalMarketXG = Math.max(1.5, Math.min(4.5, -Math.log(drawProb) * 1.25));
        double ratio = homeProb + awayProb;

        if (ratio == 0) return new double[]{totalMarketXG / 2, totalMarketXG / 2};

        return new double[]{
                round(totalMarketXG * (homeProb / ratio)),
                round(totalMarketXG * (awayProb / ratio))
        };
    }

    /**
     * Đánh giá Value Bet & Fractional Kelly Staking
     */
    private ValueBetEvaluation evaluateValueBet(double homeProb, double drawProb, double awayProb,
                                                Double homeOdd, Double drawOdd, Double awayOdd) {

        if (homeOdd == null || drawOdd == null || awayOdd == null || homeOdd <= 1.0) {
            return new ValueBetEvaluation(false, false, ValueBetType.NONE, 0.0, 0.0);
        }

        double homeEdge = (homeProb * homeOdd) - 1.0;
        double drawEdge = (drawProb * drawOdd) - 1.0;
        double awayEdge = (awayProb * awayOdd) - 1.0;

        double maxEdge = Math.max(homeEdge, Math.max(drawEdge, awayEdge));
        double edgePercentage = maxEdge * 100.0;

        ValueBetType type = ValueBetType.NONE;
        double selectedOdd = 1.0;

        if (maxEdge == homeEdge) {
            type = ValueBetType.HOME_WIN;
            selectedOdd = homeOdd;
        } else if (maxEdge == awayEdge) {
            type = ValueBetType.AWAY_WIN;
            selectedOdd = awayOdd;
        } else {
            type = ValueBetType.DRAW;
            selectedOdd = drawOdd;
        }

        if (edgePercentage >= MIN_EDGE_FREE && edgePercentage <= MAX_RELIABLE_EDGE) {
            boolean isPremium = edgePercentage >= MIN_EDGE_PREMIUM;
            double staking = edgePercentage / ((selectedOdd - 1.0) * 4.0); // Fractional Kelly 1/4
            staking = Math.round(staking * 10.0) / 10.0;
            double finalStaking = Math.max(1.0, Math.min(5.0, staking));

            return new ValueBetEvaluation(true, isPremium, type, edgePercentage, finalStaking);

        } else if (edgePercentage > MAX_RELIABLE_EDGE) {
            return new ValueBetEvaluation(true, false, type, edgePercentage, 1.0);
        }

        return new ValueBetEvaluation(false, false, ValueBetType.NONE, edgePercentage, 0.0);
    }

    private String calculateHandicapMargin(double lambdaHome, double lambdaAway) {
        double margin = lambdaHome - lambdaAway;
        if (margin > 0) return "-" + round(margin);
        if (margin < 0) return "+" + round(Math.abs(margin));
        return "0.0";
    }

    private double round(double val) {
        return Math.round(val * 100.0) / 100.0;
    }

    private double round4(double val) {
        return Math.round(val * 10000.0) / 10000.0;
    }

    private record ValueBetEvaluation(boolean hasValue, boolean isPremium, ValueBetType type, double edgePercentage, double stakingSize) {
    }

}
