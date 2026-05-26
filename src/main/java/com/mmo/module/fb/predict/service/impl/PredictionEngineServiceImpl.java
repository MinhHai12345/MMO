package com.mmo.module.fb.predict.service.impl;

import com.mmo.module.fb.entity.MatchPrediction;
import com.mmo.module.fb.entity.enums.MatchPredictionStatus;
import com.mmo.module.fb.predict.service.PredictionEngineService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.math3.distribution.PoissonDistribution;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PredictionEngineServiceImpl implements PredictionEngineService {
    private static final int MAX_GOALS = 6;

    @Override
    public void calculateMatchPredict(MatchPrediction prediction) {
        // BƯỚC 1: LẤY DỮ LIỆU THỐNG KÊ LỊCH SỬ TỪ DATABASE (MOCK LOGIC)
        // Trong thực tế, bạn sẽ viết các hàm SQL để lấy các thông số này từ bảng `matches` lịch sử đã đá xong.

        // 1.1. Số bàn thắng trung bình của TOÀN GIẢI ĐẤU (Ví dụ giải Ngoại Hạng Anh mùa này)
        double avgLeagueHomeScored = 1.52;
        double avgLeagueAwayScored = 1.24;

        // 1.2. Thống kê của Đội Nhà (Home Team) khi đá trên SÂN NHÀ
        double homeGoalsScoredAvg = 1.85; // Số bàn ghi được trung bình/trận sân nhà
        double homeGoalsConcededAvg = 0.95; // Số bàn thủng lưới trung bình/trận sân nhà

        // 1.3. Thống kê của Đội Khách (Away Team) khi đá trên SÂN KHÁCH
        double awayGoalsScoredAvg = 1.10; // Số bàn ghi được trung bình/trận sân khách
        double awayGoalsConcededAvg = 1.65; // Số bàn thủng lưới trung bình/trận sân khách

        // BƯỚC 2: TÍNH TOÁN CHỈ SỐ SỨC MẠNH (STRENGTH)
        // Công thức: Sức mạnh = (Trung bình của đội) / (Trung bình của giải)
        double homeAttackStrength = homeGoalsScoredAvg / avgLeagueHomeScored;
        double homeDefenseStrength = homeGoalsConcededAvg / avgLeagueAwayScored;

        double awayAttackStrength = awayGoalsScoredAvg / avgLeagueAwayScored;
        double awayDefenseStrength = awayGoalsConcededAvg / avgLeagueHomeScored;

        // BƯỚC 3: TÍNH TOÁN KỲ VỌNG BÀN THẮNG (LAMBDA λ)
        double lambdaHome = homeAttackStrength * awayDefenseStrength * avgLeagueHomeScored;
        double lambdaAway = awayAttackStrength * homeDefenseStrength * avgLeagueAwayScored;

        // Lưu vào Entity
        prediction.setExpectedHomeGoals(lambdaHome);
        prediction.setExpectedAwayGoals(lambdaAway);

        // BƯỚC 4: CHẠY TOÁN PHÂN PHỐI POISSON
        PoissonDistribution homeDist = new PoissonDistribution(lambdaHome);
        PoissonDistribution awayDist = new PoissonDistribution(lambdaAway);

        double[] homeGoalProbs = new double[MAX_GOALS];
        double[] awayGoalProbs = new double[MAX_GOALS];
        for (int i = 0; i < MAX_GOALS; i++) {
            homeGoalProbs[i] = homeDist.probability(i);
            awayGoalProbs[i] = awayDist.probability(i);
        }

        // Tính ma trận xác suất kết quả (Thắng - Hòa - Thua)
        double homeWinProb = 0.0;
        double drawProb = 0.0;
        double awayWinProb = 0.0;

        for (int h = 0; h < MAX_GOALS; h++) {
            for (int a = 0; a < MAX_GOALS; a++) {
                double scoreProb = homeGoalProbs[h] * awayGoalProbs[a];
                if (h > a) {
                    homeWinProb += scoreProb;
                } else if (h == a) {
                    drawProb += scoreProb;
                } else {
                    awayWinProb += scoreProb;
                }
            }
        }

        // BƯỚC 5: QUY ĐỔI SANG FAIR ODDS HỆ THỐNG
        prediction.setFairHomeOdd(homeWinProb > 0 ? 1.0 / homeWinProb : 99.0);
        prediction.setFairDrawOdd(drawProb > 0 ? 1.0 / drawProb : 99.0);
        prediction.setFairAwayOdd(awayWinProb > 0 ? 1.0 / awayWinProb : 99.0);

        // BƯỚC 6: SO SÁNH VỚI ODD FULLTIME CỦA SOFASCORE ĐỂ TÌM VALUE
        // Chỉ so sánh nếu SofaScore đã cập nhật Odd (khác null)
        if (prediction.getSofaHomeOdd() != null && prediction.getSofaHomeOdd() > 0) {
            double sofaHomeOdd = prediction.getSofaHomeOdd();

            // Nếu Odd nhà cái cao hơn Odd hệ thống tính -> Có Value lợi thế
            if (sofaHomeOdd > prediction.getFairHomeOdd()) {
                double edge = (1.0 / prediction.getFairHomeOdd()) - (1.0 / sofaHomeOdd);
                prediction.setEdgePercentage(edge * 100);
                prediction.setHasValue(true);
            } else {
                prediction.setEdgePercentage(0.0);
                prediction.setHasValue(false);
            }
        }

        // Cập nhật trạng thái sang READY (Sẵn sàng để bộ lọc Free/VIP quét vào đầu ngày)
        prediction.setStatus(MatchPredictionStatus.READY);
    }
}
