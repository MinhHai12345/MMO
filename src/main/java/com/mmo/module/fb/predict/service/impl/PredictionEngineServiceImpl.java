package com.mmo.module.fb.predict.service.impl;

import com.mmo.module.fb.crawler.model.enums.Provider;
import com.mmo.module.fb.crawler.model.sofa.SofaMatchData;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategy;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategyRegistry;
import com.mmo.module.fb.entity.MatchPrediction;
import com.mmo.module.fb.entity.enums.MatchPredictionStatus;
import com.mmo.module.fb.predict.service.PredictionEngineService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.apache.commons.math3.distribution.PoissonDistribution;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PredictionEngineServiceImpl implements PredictionEngineService {
    @Resource
    private CrawlerStrategyRegistry crawlerStrategyRegistry;

    private static final int MAX_GOALS = 9;
    private static final double MIN_EDGE_FREE = 5.0;
    private static final double MIN_EDGE_PREMIUM = 10.0;

    @Override
    public void calculateMatchPredict(MatchPrediction prediction) {
        Long homeTeamId = prediction.getMatch().getHomeTeam().getSofaScoreId();
        Long awayTeamId = prediction.getMatch().getAwayTeam().getSofaScoreId();

        // 1. Số bàn thắng trung bình của 1 đội/1 trận tại World Cup (Mốc benchmark giải đấu)
        double avgTournamentScored = 1.34;

        CrawlerStrategy strategy = crawlerStrategyRegistry.getStrategy(Provider.SOFA_SCORE);

        // 2. Lấy danh sách lịch sử trận đấu (Tối đa 29 trận gần nhất, sắp xếp từ mới đến cũ)
        List<SofaMatchData.SofaEventDTO> homeHistories = strategy.getLatestHistoriesMatchesByTeamId(homeTeamId);
        List<SofaMatchData.SofaEventDTO> awayHistories = strategy.getLatestHistoriesMatchesByTeamId(awayTeamId);

        // 3. Tính toán số bàn thắng/thua trung bình có ÁP TRỌNG SỐ cho Đội Nhà
        double[] homeCalculatedStats = calculateWeightedAverages(homeHistories, homeTeamId);
        double homeGoalsScoredAvg = homeCalculatedStats[0];   // Số bàn ghi được trung bình
        double homeGoalsConcededAvg = homeCalculatedStats[1]; // Số bàn thủng lưới trung bình

        // 4. Tính toán số bàn thắng/thua trung bình có ÁP TRỌNG SỐ cho Đội Khách
        double[] awayCalculatedStats = calculateWeightedAverages(awayHistories, awayTeamId);
        double awayGoalsScoredAvg = awayCalculatedStats[0];
        double awayGoalsConcededAvg = awayCalculatedStats[1];

        // 5. TÍNH TOÁN CHỈ SỐ SỨC MẠNH (STRENGTH)
        // Vì World Cup đá sân trung lập nên sức mạnh được so trực tiếp với trung bình bàn thắng của cả giải đấu
        double homeAttackStrength = homeGoalsScoredAvg / avgTournamentScored;
        double homeDefenseStrength = homeGoalsConcededAvg / avgTournamentScored;

        double awayAttackStrength = awayGoalsScoredAvg / avgTournamentScored;
        double awayDefenseStrength = awayGoalsConcededAvg / avgTournamentScored;

        // 6. TÍNH KỲ VỌNG BÀN THẮNG (LAMBDA λ) CHO 90 PHÚT CHÍNH THỨC
        double lambdaHome = homeAttackStrength * awayDefenseStrength * avgTournamentScored;
        double lambdaAway = awayAttackStrength * homeDefenseStrength * avgTournamentScored;

        // Chống lỗi toán học âm hoặc bằng 0
        if (lambdaHome <= 0) lambdaHome = 1.0;
        if (lambdaAway <= 0) lambdaAway = 1.0;

        prediction.setExpectedHomeGoals(round(lambdaHome));
        prediction.setExpectedAwayGoals(round(lambdaAway));

        // 7. CHẠY MÔ HÌNH TOÁN POISSON ĐỂ TÌM XÁC SUẤT TRẬN ĐẤU (Thắng - Hòa - Thua)
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

        for (int h = 0; h < MAX_GOALS; h++) {
            for (int a = 0; a < MAX_GOALS; a++) {
                double scoreProb = homeGoalProbs[h] * awayGoalProbs[a];
                if (h > a) homeWinProb += scoreProb;
                else if (h == a) drawProb += scoreProb;
                else awayWinProb += scoreProb;
            }
        }

        // 8. ĐỔI XÁC SUẤT RA FAIR ODDS CỦA HỆ THỐNG H2
        prediction.setFairHomeOdd(homeWinProb > 0 ? round(1.0 / homeWinProb) : 99.0);
        prediction.setFairDrawOdd(drawProb > 0 ? round(1.0 / drawProb) : 99.0);
        prediction.setFairAwayOdd(awayWinProb > 0 ? round(1.0 / awayWinProb) : 99.0);

        // 9. ĐỐI CHIẾU THỊ TRƯỜNG SOFA ODDS ĐỂ TÌM GIÁ TRỊ LỆCH (EDGE %)
        if (prediction.getSofaHomeOdd() != null && prediction.getSofaHomeOdd() > 0) {
            double homeEdge = (homeWinProb * prediction.getSofaHomeOdd()) - 1;
            double drawEdge = (drawProb * prediction.getSofaDrawOdd()) - 1;
            double awayEdge = (awayWinProb * prediction.getSofaAwayOdd()) - 1;

            double maxEdge = Math.max(homeEdge, Math.max(drawEdge, awayEdge));
            double maxEdgePercentage = maxEdge * 100;

            prediction.setEdgePercentage(round(maxEdgePercentage));

            // Trích xuất tên đội để làm chuỗi text Recommended sống động
            String homeTeamName = prediction.getMatch().getHomeTeam().getName();
            String awayTeamName = prediction.getMatch().getAwayTeam().getName();

            // Khởi tạo các biến chứa tỷ lệ cược đã chọn để đưa vào công thức Kelly
            double selectedSofaOdd = 1.0;

            // Bốc chuẩn xác cửa cược tối ưu nhất dựa trên toán học
            if (maxEdge == homeEdge) {
                prediction.setRecommendedPick(homeTeamName + " (Home Win)");
                selectedSofaOdd = prediction.getSofaHomeOdd();
            } else if (maxEdge == awayEdge) {
                prediction.setRecommendedPick(awayTeamName + " (Away Win)");
                selectedSofaOdd = prediction.getSofaAwayOdd();
            } else {
                prediction.setRecommendedPick("Draw (X)");
                selectedSofaOdd = prediction.getSofaDrawOdd();
            }

            if (maxEdgePercentage >= MIN_EDGE_FREE) {
                prediction.setHasValue(true);
                prediction.setPremium(maxEdgePercentage >= MIN_EDGE_PREMIUM);

                // 🎯 TIẾN HÀNH TÍNH VÀ LƯU THUỘC TÍNH SMART STAKING (Fractional Kelly 1/4)
                double smartStaking = maxEdgePercentage / ((selectedSofaOdd - 1.0) * 4.0);
                smartStaking = Math.round(smartStaking * 10.0) / 10.0; // Làm tròn 1 chữ số thập phân

                // Khống chế biên an toàn (Hard Caps) bảo vệ tài khoản khách VIP
                if (smartStaking > 5.0) smartStaking = 5.0;
                if (smartStaking < 1.0) smartStaking = 1.0;

                prediction.setSmartStakingSize(smartStaking);
            } else {
                prediction.setHasValue(false);
                prediction.setPremium(false);
                prediction.setRecommendedPick("NO VALUE");
                prediction.setSmartStakingSize(0.0);
            }
        } else {
            // Trường hợp không có dữ liệu Sofa Odds từ hệ thống Crawl
            prediction.setRecommendedPick("NO ODDS DATA");
            prediction.setSmartStakingSize(0.0);
            prediction.setEdgePercentage(0.0);
            prediction.setHasValue(false);
        }

        prediction.setStatus(MatchPredictionStatus.READY);
    }

    private double[] calculateWeightedAverages(List<SofaMatchData.SofaEventDTO> histories, Long teamId) {
        double totalWeightedGoalsScored = 0.0;
        double totalWeightedGoalsConceded = 0.0;
        double totalWeight = 0.0;

        for (int i = 0; i < histories.size(); i++) {
            SofaMatchData.SofaEventDTO match = histories.get(i);
            double weight = 1.0;

            if (i < 5) {
                weight = 3.0;
            } else if (i < 15) {
                weight = 2.0;
            }
            double goalsScored;
            double goalsConceded;

            if (teamId.equals(match.getHomeTeam().getId())) {
                goalsScored = match.getHomeScore().getCurrent();
                goalsConceded = match.getAwayScore().getCurrent();
            } else {
                goalsScored = match.getAwayScore().getCurrent();
                goalsConceded = match.getHomeScore().getCurrent();
            }

            totalWeightedGoalsScored += (goalsScored * weight);
            totalWeightedGoalsConceded += (goalsConceded * weight);
            totalWeight += weight;
        }

        if (totalWeight == 0) return new double[]{1.2, 1.2};

        double avgScored = totalWeightedGoalsScored / totalWeight;
        double avgConceded = totalWeightedGoalsConceded / totalWeight;

        return new double[]{avgScored, avgConceded};
    }

    private double round(double value) {
        long factor = (long) Math.pow(10, 2);
        return (double) Math.round(value * factor) / factor;
    }

    /**
     * Tính toán Staking dựa trên Fractional Kelly Criterion (Hệ số an toàn 1/4)
     */
    public double calculateSmartStaking(double sofaOdds, double edgePercentage) {
        if (edgePercentage <= 0 || sofaOdds <= 1.0) {
            return 0.0;
        }

        // Công thức thực chiến: Smart Staking = Edge % / ((Odds - 1) * 4)
        double staking = edgePercentage / ((sofaOdds - 1.0) * 4.0);

        // Làm tròn toán học lấy 1 chữ số thập phân (Ví dụ: 1.818... -> 1.8)
        staking = Math.round(staking * 10.0) / 10.0;

        // Quy tắc Hard Caps bảo vệ nguồn vốn của nhà đầu tư VIP
        if (staking > 5.0) return 5.0;
        return Math.max(staking, 1.0);

    }

    /**
     * Gom nhóm danh sách 10 trận đấu theo Khung giờ đá (Sắp xếp tăng dần theo thời gian)
     */
    public Map<Integer, List<MatchPrediction>> groupMatchesByHour(List<MatchPrediction> matches) {
        return matches.stream()
                .collect(Collectors.groupingBy(
                        mp -> mp.getKickoffTime().getHour(),
                        TreeMap::new, // Sử dụng TreeMap để tự động sắp xếp khung giờ từ sớm đến muộn
                        Collectors.toList()
                ));
    }
}
