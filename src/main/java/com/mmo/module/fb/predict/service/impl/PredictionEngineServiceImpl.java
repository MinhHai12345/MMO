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
import org.apache.commons.math3.distribution.PoissonDistribution;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PredictionEngineServiceImpl implements PredictionEngineService {
    @Resource
    private CrawlerStrategyRegistry crawlerStrategyRegistry;

    private static final int MAX_GOALS = 9;
    private static final double MIN_EDGE_FREE = 5.0;
    private static final double MIN_EDGE_PREMIUM = 10.0;
    private static final double MAX_RELIABLE_EDGE = 50.0;

    @Override
    public void calculateMatchPredict(MatchPrediction prediction) {
        Long homeTeamId = prediction.getMatch().getHomeTeam().getSofaScoreId();
        Long awayTeamId = prediction.getMatch().getAwayTeam().getSofaScoreId();

        String homeTeamName = prediction.getMatch().getHomeTeam().getName();
        String awayTeamName = prediction.getMatch().getAwayTeam().getName();

        CrawlerStrategy strategy = crawlerStrategyRegistry.getStrategy(Provider.SOFA_SCORE);

        // 1. LẤY LỊCH SỬ PHONG ĐỘ (Tối đa 29 trận gần nhất)
        List<SofaMatchesData.SofaEventDTO> homeHistories = strategy.getLatestHistoriesMatchesByTeamId(homeTeamId);
        List<SofaMatchesData.SofaEventDTO> awayHistories = strategy.getLatestHistoriesMatchesByTeamId(awayTeamId);

        // 2. TÍNH TRUNG BÌNH BÀN THẮNG/THUA CÓ ÁP TRỌNG SỐ THỜI GIAN & VỊ THẾ SÂN
        double[] homeStats = calculateAdvancedWeightedAverages(homeHistories, homeTeamId, true);
        double homeGoalsScoredAvg = homeStats[0];
        double homeGoalsConcededAvg = homeStats[1];

        double[] awayStats = calculateAdvancedWeightedAverages(awayHistories, awayTeamId, false);
        double awayGoalsScoredAvg = awayStats[0];
        double awayGoalsConcededAvg = awayStats[1];

        // 3. TÍNH TOÁN BASELINE GIẢI ĐẤU ĐỘNG (Local Tournament Baseline)
        double avgTournamentScored = (homeGoalsScoredAvg + awayGoalsScoredAvg) / 2.0;
        if (avgTournamentScored <= 0.5) avgTournamentScored = 1.25; // Biên an toàn chống tịt ngòi sâu

        // 4. TÍNH CHỈ SỐ SỨC MẠNH (STRENGTH) DỰA TRÊN LOCAL BASELINE
        double homeAttackStrength = homeGoalsScoredAvg / avgTournamentScored;
        double homeDefenseStrength = homeGoalsConcededAvg / avgTournamentScored;

        double awayAttackStrength = awayGoalsScoredAvg / avgTournamentScored;
        double awayDefenseStrength = awayGoalsConcededAvg / avgTournamentScored;

        // 5. TÍNH KỲ VỌNG BÀN THẮNG H2 (H2 CUSTOM xG - LAMBDA λ)
        double lambdaHome = homeAttackStrength * awayDefenseStrength * avgTournamentScored;
        double lambdaAway = awayAttackStrength * homeDefenseStrength * avgTournamentScored;

        if (lambdaHome < 0.2) lambdaHome = 0.2;
        if (lambdaAway < 0.2) lambdaAway = 0.2;

        prediction.setExpectedHomeGoals(round(lambdaHome));
        prediction.setExpectedAwayGoals(round(lambdaAway));

        double h2TotalXG = lambdaHome + lambdaAway;
        prediction.setH2TotalXG(round(h2TotalXG));

        double h2HandicapMargin = lambdaHome - lambdaAway;
        if (h2HandicapMargin > 0) {
            prediction.setH2HandicapMargin("-" + round(h2HandicapMargin));
        } else if (h2HandicapMargin < 0) {
            prediction.setH2HandicapMargin("+" + round(Math.abs(h2HandicapMargin)));
        } else {
            prediction.setH2HandicapMargin("0.0 (Draw No Bet)");
        }

        // 6. CHẠY MÔ HÌNH POISSON ĐỂ TÌM XÁC SUẤT H2
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
                if ((h + a) < 2.5) {
                    probUnder25 += scoreProb;
                }
                if (h > a) homeWinProb += scoreProb;
                else if (h == a) drawProb += scoreProb;
                else awayWinProb += scoreProb;
            }
        }
        double probOver25 = 1.0 - probUnder25;
        // Lưu xác suất dạng % (Làm tròn để render lên content cho đẹp)
        prediction.setH2ProbUnder25(round(probUnder25 * 100));
        prediction.setH2ProbOver25(round(probOver25 * 100));

        // Tự động đồng bộ tìm Top 3 Tỷ số có xác suất cao nhất toàn cục
        calculatePoissonCorrectScores(prediction, homeGoalProbs, awayGoalProbs);

        // Đổi xác suất H2 sang Fair Odds
        prediction.setFairHomeOdd(homeWinProb > 0 ? round(1.0 / homeWinProb) : 99.0);
        prediction.setFairDrawOdd(drawProb > 0 ? round(1.0 / drawProb) : 99.0);
        prediction.setFairAwayOdd(awayWinProb > 0 ? round(1.0 / awayWinProb) : 99.0);

        // 7. XÁC ĐỊNH MOST LIKELY WINNER (Đội có tỷ lệ thắng cao nhất về lý thuyết)
        double maxProb = Math.max(homeWinProb, Math.max(drawProb, awayWinProb));
        if (maxProb == homeWinProb) {
            prediction.setMostLikelyWinner(homeTeamName + " (" + Math.round(homeWinProb * 100) + "%)");
        } else if (maxProb == awayWinProb) {
            prediction.setMostLikelyWinner(awayTeamName + " (" + Math.round(awayWinProb * 100) + "%)");
        } else {
            prediction.setMostLikelyWinner("Draw (" + Math.round(drawProb * 100) + "%)");
        }

        // 8. BẺ NGƯỢC MARKET ODDS RA MARKET xG & TÌM VALUE BET
        if (prediction.getSofaHomeOdd() != null && prediction.getSofaHomeOdd() > 0) {
            // Luồng 8.1: Reverse Market xG
            double[] marketXG = calculateMarketExpectedGoals(
                    prediction.getSofaHomeOdd(),
                    prediction.getSofaDrawOdd(),
                    prediction.getSofaAwayOdd()
            );
            prediction.setMarketHomeXG(marketXG[0]);
            prediction.setMarketAwayXG(marketXG[1]);

            // Luồng 8.2: Tính Toán Lợi Thế Cược (Value Edge)
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

            // Luồng 8.3: Phân Bổ Vốn Smart Staking (Fractional Kelly 1/4)
            if (maxEdgePercentage >= MIN_EDGE_FREE && maxEdgePercentage <= MAX_RELIABLE_EDGE) {
                prediction.setHasValue(true);
                prediction.setPremium(maxEdgePercentage >= MIN_EDGE_PREMIUM);
                prediction.setValueBetPick(bestEdgePick + " [Edge: +" + round(maxEdgePercentage) + "%]");

                double smartStaking = maxEdgePercentage / ((selectedSofaOdd - 1.0) * 4.0);
                smartStaking = Math.round(smartStaking * 10.0) / 10.0;

                if (smartStaking > 5.0) smartStaking = 5.0;
                if (smartStaking < 1.0) smartStaking = 1.0;
                prediction.setSmartStakingSize(smartStaking);

            } else if (maxEdgePercentage > MAX_RELIABLE_EDGE) {
                // Xử lý dữ liệu Outlier nhiễu: Ép dòng tiền về mức an toàn bảo vệ ví người dùng
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
        } else {
            prediction.setValueBetPick("NO ODDS DATA");
            prediction.setSmartStakingSize(0.0);
            prediction.setEdgePercentage(0.0);
            prediction.setHasValue(false);
            prediction.setMarketHomeXG(0.0);
            prediction.setMarketAwayXG(0.0);
        }

        prediction.setStatus(MatchPredictionStatus.READY);
    }

    /**
     * Thuật toán bẻ ngược Market Odds (1X2) về cấu trúc Market xG ngầm định
     */
    private double[] calculateMarketExpectedGoals(double homeOdd, double drawOdd, double awayOdd) {
        double rawHomeProb = 1.0 / homeOdd;
        double rawDrawProb = 1.0 / drawOdd;
        double rawAwayProb = 1.0 / awayOdd;
        double totalImplicitProb = rawHomeProb + rawDrawProb + rawAwayProb;

        // Loại bỏ biên lợi nhuận (Margin) của nhà cái
        double homeProb = rawHomeProb / totalImplicitProb;
        double drawProb = rawDrawProb / totalImplicitProb;
        double awayProb = rawAwayProb / totalImplicitProb;

        // Ước lượng tổng bàn thắng dựa trên hàm mật độ Poisson của xác suất hòa
        double totalMarketXG = -Math.log(drawProb) * 1.25;
        if (totalMarketXG < 1.5) totalMarketXG = 1.5;
        if (totalMarketXG > 4.5) totalMarketXG = 4.5;

        double ratio = homeProb + awayProb;
        if (ratio == 0) return new double[]{totalMarketXG / 2, totalMarketXG / 2};

        // Phân bổ tỷ lệ bàn thắng kỳ vọng cho từng đội
        double marketHomeXG = totalMarketXG * (homeProb / ratio);
        double marketAwayXG = totalMarketXG * (awayProb / ratio);

        return new double[]{round(marketHomeXG), round(marketAwayXG)};
    }

    /**
     * Tính phong độ nâng cao: Áp trọng số thời gian thực và vị thế sân đấu đối xứng
     */
    private double[] calculateAdvancedWeightedAverages(List<SofaMatchesData.SofaEventDTO> histories, Long teamId, boolean isHomePosition) {
        double totalWeightedGoalsScored = 0.0;
        double totalWeightedGoalsConceded = 0.0;
        double totalWeight = 0.0;

        for (int i = 0; i < histories.size(); i++) {
            SofaMatchesData.SofaEventDTO match = histories.get(i);
            if (!"finished".equalsIgnoreCase(match.getStatus().getType())) continue;

            boolean isMatchHome = teamId.equals(match.getHomeTeam().getId());

            // Trọng số 1: Khoảng cách thời gian (Gần nhất = Trọng số cao nhất)
            double recencyWeight = 1.0;
            if (i < 5) recencyWeight = 3.0;
            else if (i < 12) recencyWeight = 2.0;

            // Trọng số 2: Tính tương đồng vị thế sân đấu (Home vs Home | Away vs Away)
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

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Quét ma trận xác suất và lấy ra chính xác TOP 3 tỷ số có tỷ lệ xảy ra cao nhất
     */
    private void calculatePoissonCorrectScores(MatchPrediction prediction, double[] homeGoalProbs, double[] awayGoalProbs) {
        List<ScoreProbability> scoreList = new ArrayList<>();
        for (int h = 0; h < MAX_GOALS; h++) {
            for (int a = 0; a < MAX_GOALS; a++) {
                double scoreProb = homeGoalProbs[h] * awayGoalProbs[a];
                scoreList.add(new ScoreProbability(h + "-" + a, scoreProb));
            }
        }
        Collections.sort(scoreList);

        String topScores = scoreList.stream()
                .limit(3)
                .map(ScoreProbability::getScore)
                .collect(Collectors.joining(", "));
        prediction.setTopCorrectScores(topScores);
    }
}
