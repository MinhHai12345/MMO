package com.mmo.module.fb.predict.service.impl;

import com.mmo.module.fb.crawler.model.enums.Provider;
import com.mmo.module.fb.crawler.model.sofa.SofaMatchesData;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategy;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategyRegistry;
import com.mmo.module.fb.entity.MatchPrediction;
import com.mmo.module.fb.entity.enums.MatchPredictionStatus;
import com.mmo.module.fb.predict.model.ScoreProbability;
import com.mmo.module.fb.predict.service.PredictionEngineService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.distribution.PoissonDistribution;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionEngineServiceImpl implements PredictionEngineService {
    @Resource
    private CrawlerStrategyRegistry crawlerStrategyRegistry;

    // Constants cấu hình hệ thống
    private static final int MAX_GOALS = 9;
    private static final double MIN_EDGE_FREE = 5.0;
    private static final double MIN_EDGE_PREMIUM = 10.0;
    private static final double MAX_RELIABLE_EDGE = 50.0;
    private static final double MAX_RANK_MODIFIER = 0.15; // Giới hạn ảnh hưởng thứ hạng tối đa 15%
    private static final double ABSOLUTE_MIN_LAMBDA = 0.2;

    @Override
    public void calculateMatchPredict(MatchPrediction prediction) {
        try {
            Long homeTeamId = prediction.getMatch().getHomeTeam().getSofaScoreId();
            Long awayTeamId = prediction.getMatch().getAwayTeam().getSofaScoreId();
            String homeTeamName = prediction.getMatch().getHomeTeam().getName();
            String awayTeamName = prediction.getMatch().getAwayTeam().getName();

            CrawlerStrategy strategy = crawlerStrategyRegistry.getStrategy(Provider.SOFA_SCORE);

            // Step 2: Cào và kiểm tra lịch sử phong độ
            List<SofaMatchesData.SofaEventDTO> homeHistories = strategy.getLatestHistoriesMatchesByTeamId(homeTeamId);
            List<SofaMatchesData.SofaEventDTO> awayHistories = strategy.getLatestHistoriesMatchesByTeamId(awayTeamId);

            if (homeHistories.isEmpty() || awayHistories.isEmpty()) {
                log.info("Missing match histories for teams in Match: {} vs {}", homeTeamName, awayTeamName);
                return;
            }

            // Step 3: Tính trung bình số bàn thắng/thua động có áp trọng số thời gian & sân đấu
            double[] homeStats = calculateAdvancedWeightedAverages(homeHistories, homeTeamId, true);
            double[] awayStats = calculateAdvancedWeightedAverages(awayHistories, awayTeamId, false);

            // Step 4: Chuẩn hóa Tournament Baseline
            // CHUẨN NHẤT: Nên lấy từ database cấu hình giải đấu. Hiện tại dùng giải pháp fallback an toàn 2.5 bàn/trận (H1+H2) -> H2 ≈ 1.35
            double avgTournamentScored = 1.35;
//            if (prediction.getMatch().getTournament() != null && prediction.getMatch().getTournament().getAvgGoals() > 0) {
//                avgTournamentScored = prediction.getMatch().getTournament().getAvgGoals() * 0.55; // Ước lượng riêng cho H2
//            }

            // Step 5: Tính toán Chỉ số Sức mạnh (Strength) gốc
            double homeAttackStrength = homeStats[0] / avgTournamentScored;
            double homeDefenseStrength = homeStats[1] / avgTournamentScored;
            double awayAttackStrength = awayStats[0] / avgTournamentScored;
            double awayDefenseStrength = awayStats[1] / avgTournamentScored;

            // Step 6: Tích hợp Hệ số Thứ hạng đối xứng (Ranking Modifier)
            Integer homeRank = prediction.getMatch().getHomeTeam().getRanking();
            Integer awayRank = prediction.getMatch().getAwayTeam().getRanking();
            if (homeRank != null && awayRank != null && homeRank > 0 && awayRank > 0) {
                int rankDiff = awayRank - homeRank;
                double rankModifier = Math.max(-MAX_RANK_MODIFIER, Math.min(MAX_RANK_MODIFIER, rankDiff * 0.01));

                homeAttackStrength *= (1.0 + rankModifier);
                homeDefenseStrength *= (1.0 - rankModifier);
                awayAttackStrength *= (1.0 - rankModifier);
                awayDefenseStrength *= (1.0 + rankModifier);
            }

            // Step 7: Tính kỳ vọng bàn thắng H2 (Lambda λ)
            double lambdaHome = Math.max(ABSOLUTE_MIN_LAMBDA, homeAttackStrength * awayDefenseStrength * avgTournamentScored);
            double lambdaAway = Math.max(ABSOLUTE_MIN_LAMBDA, awayAttackStrength * homeDefenseStrength * avgTournamentScored);

            prediction.setExpectedHomeGoals(round(lambdaHome));
            prediction.setExpectedAwayGoals(round(lambdaAway));
            prediction.setH2TotalXG(round(lambdaHome + lambdaAway));
            prediction.setH2HandicapMargin(calculateHandicapMargin(lambdaHome, lambdaAway));

            // Step 8: Chạy phân phối xác suất Poisson & Khớp ma trận tỷ số
            computePoissonProbabilities(prediction, lambdaHome, lambdaAway, homeTeamName, awayTeamName);

            // Step 9: Phân tích Lợi thế Toán học (Value Bet) & Quản lý vốn Kelly
            evaluateMarketValueAndStaking(prediction, homeTeamName, awayTeamName);

            prediction.setStatus(MatchPredictionStatus.READY);

        } catch (Exception e) {
            log.error("Critical error while calculating prediction for Match ID: {}", prediction.getMatch().getId(), e);
        }
    }

    /**
     * Helper: Phân tích Poisson và các xác suất kết quả
     */
    private void computePoissonProbabilities(MatchPrediction prediction, double lambdaHome, double lambdaAway, String homeTeamName, String awayTeamName) {
        PoissonDistribution homeDist = new PoissonDistribution(lambdaHome);
        PoissonDistribution awayDist = new PoissonDistribution(lambdaAway);

        double[] homeGoalProbs = new double[MAX_GOALS];
        double[] awayGoalProbs = new double[MAX_GOALS];
        for (int i = 0; i < MAX_GOALS; i++) {
            homeGoalProbs[i] = homeDist.probability(i);
            awayGoalProbs[i] = awayDist.probability(i);
        }

        double homeWinProb = 0.0;
        double drawProb = 0.0;
        double awayWinProb = 0.0;
        double probUnder25 = 0.0;

        for (int h = 0; h < MAX_GOALS; h++) {
            for (int a = 0; a < MAX_GOALS; a++) {
                double scoreProb = homeGoalProbs[h] * awayGoalProbs[a];
                if ((h + a) < 2.5) probUnder25 += scoreProb;
                if (h > a) homeWinProb += scoreProb;
                else if (h == a) drawProb += scoreProb;
                else awayWinProb += scoreProb;
            }
        }

        prediction.setH2ProbUnder25(round(probUnder25 * 100));
        prediction.setH2ProbOver25(round((1.0 - probUnder25) * 100));

        prediction.setFairHomeOdd(homeWinProb > 0 ? round(1.0 / homeWinProb) : 99.0);
        prediction.setFairDrawOdd(drawProb > 0 ? round(1.0 / drawProb) : 99.0);
        prediction.setFairAwayOdd(awayWinProb > 0 ? round(1.0 / awayWinProb) : 99.0);

        // Tìm Top 3 tỷ số có xác suất cao nhất
        calculatePoissonCorrectScores(prediction, homeGoalProbs, awayGoalProbs);

        // Xác định đội có tỷ lệ thắng lý thuyết cao nhất
        double maxProb = Math.max(homeWinProb, Math.max(drawProb, awayWinProb));
        if (maxProb == homeWinProb) {
            prediction.setMostLikelyWinner(homeTeamName + " (" + Math.round(homeWinProb * 100) + "%)");
        } else if (maxProb == awayWinProb) {
            prediction.setMostLikelyWinner(awayTeamName + " (" + Math.round(awayWinProb * 100) + "%)");
        } else {
            prediction.setMostLikelyWinner("Draw (" + Math.round(drawProb * 100) + "%)");
        }

        // Tạm lưu xác suất gốc để phục vụ tính toán bước sau
        prediction.setTempHomeProb(homeWinProb);
        prediction.setTempDrawProb(drawProb);
        prediction.setTempAwayProb(awayWinProb);
    }

    /**
     * Helper: Phân tích tỷ lệ nhà cái, tìm Edge và tính quy mô dòng tiền Kelly
     */
    private void evaluateMarketValueAndStaking(MatchPrediction prediction, String homeTeamName, String awayTeamName) {
        if (prediction.getSofaHomeOdd() == null || prediction.getSofaHomeOdd() <= 0) {
            prediction.setValueBetPick("NO ODDS DATA");
            prediction.setSmartStakingSize(0.0);
            prediction.setEdgePercentage(0.0);
            prediction.setHasValue(false);
            prediction.setMarketHomeXG(0.0);
            prediction.setMarketAwayXG(0.0);
            return;
        }

        // Bẻ ngược kèo nhà cái ra Market xG ngầm định
        double[] marketXG = calculateMarketExpectedGoals(prediction.getSofaHomeOdd(), prediction.getSofaDrawOdd(), prediction.getSofaAwayOdd());
        prediction.setMarketHomeXG(marketXG[0]);
        prediction.setMarketAwayXG(marketXG[1]);

        // Lấy lại xác suất gốc từ bước trước
        double homeWinProb = prediction.getTempHomeProb();
        double drawProb = prediction.getTempDrawProb();
        double awayWinProb = prediction.getTempAwayProb();

        // Tính toán lợi thế Edge toán học
        double homeEdge = (homeWinProb * prediction.getSofaHomeOdd()) - 1;
        double drawEdge = (drawProb * prediction.getSofaDrawOdd()) - 1;
        double awayEdge = (awayWinProb * prediction.getSofaAwayOdd()) - 1;

        double maxEdge = Math.max(homeEdge, Math.max(drawEdge, awayEdge));
        double maxEdgePercentage = maxEdge * 100;
        prediction.setEdgePercentage(round(maxEdgePercentage));

        String bestEdgePick = "";
        double selectedSofaOdd = 1.0;

        if (maxEdge == homeEdge) {
            bestEdgePick = homeTeamName + " (Home Win)";
            selectedSofaOdd = prediction.getSofaHomeOdd();
        } else if (maxEdge == awayEdge) {
            bestEdgePick = awayTeamName + " (Away Win)";
            selectedSofaOdd = prediction.getSofaAwayOdd();
        } else {
            bestEdgePick = "Draw (X)";
            selectedSofaOdd = prediction.getSofaDrawOdd();
        }

        // Xử lý phân bổ staking theo bộ lọc Edge
        if (maxEdgePercentage >= MIN_EDGE_FREE && maxEdgePercentage <= MAX_RELIABLE_EDGE) {
            prediction.setHasValue(true);
            prediction.setPremium(maxEdgePercentage >= MIN_EDGE_PREMIUM);
            prediction.setValueBetPick(bestEdgePick + " [Edge: +" + round(maxEdgePercentage) + "%]");

            // Công thức Fractional Kelly 1/4 bảo vệ tài khoản
            double smartStaking = maxEdgePercentage / ((selectedSofaOdd - 1.0) * 4.0);
            smartStaking = Math.round(smartStaking * 10.0) / 10.0;
            prediction.setSmartStakingSize(Math.max(1.0, Math.min(5.0, smartStaking)));

        } else if (maxEdgePercentage > MAX_RELIABLE_EDGE) {
            // Trường hợp Edge quá cao bất thường -> Khóa dòng tiền ở mức an toàn tối thiểu 1% chống nhiễu
            prediction.setHasValue(true);
            prediction.setPremium(false);
            prediction.setValueBetPick(bestEdgePick + " [High Variance Edge]");
            prediction.setSmartStakingSize(1.0);
        } else {
            prediction.setHasValue(false);
            prediction.setPremium(false);
            prediction.setValueBetPick("NO VALUE DETECTED");
            prediction.setSmartStakingSize(0.0);
        }
    }

    private double[] calculateMarketExpectedGoals(double homeOdd, double drawOdd, double awayOdd) {
        double rawHomeProb = 1.0 / homeOdd;
        double rawDrawProb = 1.0 / drawOdd;
        double rawAwayProb = 1.0 / awayOdd;
        double totalImplicitProb = rawHomeProb + rawDrawProb + rawAwayProb;

        double homeProb = rawHomeProb / totalImplicitProb;
        double drawProb = rawDrawProb / totalImplicitProb;
        double awayProb = rawAwayProb / totalImplicitProb;

        double totalMarketXG = -Math.log(drawProb) * 1.25;
        totalMarketXG = Math.max(1.5, Math.min(4.5, totalMarketXG));

        double ratio = homeProb + awayProb;
        if (ratio == 0) return new double[]{totalMarketXG / 2, totalMarketXG / 2};

        return new double[]{round(totalMarketXG * (homeProb / ratio)), round(totalMarketXG * (awayProb / ratio))};
    }

    private double[] calculateAdvancedWeightedAverages(List<SofaMatchesData.SofaEventDTO> histories, Long teamId, boolean isHomePosition) {
        double totalWeightedGoalsScored = 0.0;
        double totalWeightedGoalsConceded = 0.0;
        double totalWeight = 0.0;

        for (int i = 0; i < histories.size(); i++) {
            SofaMatchesData.SofaEventDTO match = histories.get(i);
            if (match == null || match.getStatus() == null || !"finished".equalsIgnoreCase(match.getStatus().getType())) continue;

            boolean isMatchHome = teamId.equals(match.getHomeTeam().getId());

            // Trọng số Recency (Phong độ gần đây)
            double recencyWeight = (i < 5) ? 3.0 : ((i < 12) ? 2.0 : 1.0);
            // Trọng số Tương đồng Sân đấu
            double positionWeight = (isHomePosition == isMatchHome) ? 1.5 : 1.0;
            double finalWeight = recencyWeight * positionWeight;

            double goalsScored = isMatchHome ? match.getHomeScore().getCurrent() : match.getAwayScore().getCurrent();
            double goalsConceded = isMatchHome ? match.getAwayScore().getCurrent() : match.getHomeScore().getCurrent();

            totalWeightedGoalsScored += (goalsScored * finalWeight);
            totalWeightedGoalsConceded += (goalsConceded * finalWeight);
            totalWeight += finalWeight;
        }

        if (totalWeight == 0) return new double[]{1.2, 1.2};
        return new double[]{totalWeightedGoalsScored / totalWeight, totalWeightedGoalsConceded / totalWeight};
    }

    private String calculateHandicapMargin(double lambdaHome, double lambdaAway) {
        double margin = lambdaHome - lambdaAway;
        if (margin > 0) return "-" + round(margin);
        if (margin < 0) return "+" + round(Math.abs(margin));
        return "0.0 (Draw No Bet)";
    }

    private void calculatePoissonCorrectScores(MatchPrediction prediction, double[] homeGoalProbs, double[] awayGoalProbs) {
        List<ScoreProbability> scoreList = new ArrayList<>();
        for (int h = 0; h < MAX_GOALS; h++) {
            for (int a = 0; a < MAX_GOALS; a++) {
                scoreList.add(new ScoreProbability(h + "-" + a, homeGoalProbs[h] * awayGoalProbs[a]));
            }
        }
        Collections.sort(scoreList);

        String topScores = scoreList.stream()
                .limit(3)
                .map(ScoreProbability::getScore)
                .collect(Collectors.joining(", "));
        prediction.setTopCorrectScores(topScores);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
