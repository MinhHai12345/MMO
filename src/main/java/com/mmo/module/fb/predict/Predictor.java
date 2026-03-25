package com.mmo.module.fb.predict;

import com.mmo.module.fb.entity.PerformanceHistory;
import com.mmo.module.fb.model.MatchOddsInsight;
import org.apache.commons.math3.distribution.PoissonDistribution;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Predictor {
    private static final int MAX_GOALS = 8;

    public static double getExpectedXG(List<PerformanceHistory> histories) {
        if (histories == null || histories.isEmpty()) {
            return 0.0;
        }
        histories.sort(Comparator.comparing(PerformanceHistory::getDate).reversed());
        double weightedSum = 0;
        double totalWeights = 0;
        // Sử dụng hệ số giảm thiểu (Decay Factor). Ví dụ: 0.8
        double decayFactor = 0.8;
        for (int i = 0; i < histories.size(); i++) {
            double w = Math.pow(decayFactor, i);
            weightedSum += histories.get(i).getXG() * w;
            totalWeights += w;
        }
        return weightedSum / totalWeights;
    }

    public static MatchOddsInsight calculateOddsInsight(double homeXG, double awayXG) {
        double homeWinProb = 0;
        double awayWinProb = 0;
        double drawProb = 0;
        double over25Prob = 0;
        Map<String, Double> scoreMap = new HashMap<>();

        // Khởi tạo phân phối Poisson cho từng đội
        PoissonDistribution homeDist = new PoissonDistribution(homeXG);
        PoissonDistribution awayDist = new PoissonDistribution(awayXG);

        for (int h = 0; h <= MAX_GOALS; h++) {
            for (int a = 0; a <= MAX_GOALS; a++) {
                // Xác suất xảy ra tỉ số h - a
                double prob = homeDist.probability(h) * awayDist.probability(a);

                // 1. Phân loại kết quả (W-D-L)
                if (h > a) homeWinProb += prob;
                else if (h < a) awayWinProb += prob;
                else drawProb += prob;

                // 2. Tính Tài/Xỉu 2.5
                if (h + a > 2.5) over25Prob += prob;

                // 3. Lưu xác suất tỉ số chính xác (Chỉ lấy các tỉ số khả thi 0-4 bàn)
                if (h <= 4 && a <= 4) {
                    scoreMap.put(h + "-" + a, prob);
                }
            }
        }

        return MatchOddsInsight.builder()
                .homeOdds(homeWinProb)
                .awayOdds(awayWinProb)
                .drawOdds(drawProb)
                .over25Odds(over25Prob)
                .scoreMap(scoreMap)
                .build();
    }

    // 2. Dự đoán xác suất dựa trên Poisson
    public static void predictMatch(String homeName, double homeExp, String awayName, double awayExp) {
        PoissonDistribution homeDist = new PoissonDistribution(homeExp);
        PoissonDistribution awayDist = new PoissonDistribution(awayExp);

        double homeWin = 0, draw = 0, awayWin = 0;
        Map<String, Double> scores = new HashMap<>();

        for (int h = 0; h <= 5; h++) {
            for (int a = 0; a <= 5; a++) {
                double prob = homeDist.probability(h) * awayDist.probability(a);
                if (h > a) homeWin += prob;
                else if (h == a) draw += prob;
                else awayWin += prob;

                if (prob > 0.08) scores.put(h + "-" + a, prob);
            }
        }

        System.out.println("--- DỰ ĐOÁN KẾT QUẢ " + homeName + " VS " + awayName + " ---");
        System.out.printf("Tỉ lệ %s thắng: %.2f%%\n", homeName, homeWin * 100);
        System.out.printf("Tỉ lệ Hòa: %.2f%%\n", draw * 100);
        System.out.printf("Tỉ lệ %s thắng: %.2f%%\n", awayName, awayWin * 100);

        System.out.println("\nCác tỉ số dễ xảy ra nhất:");
        scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(3)
                .forEach(e -> System.out.printf("- Tỉ số %s: %.2f%%\n", e.getKey(), e.getValue() * 100));
    }

    // 3. Viết Content Script tự động
    public static void generateScript(String home, String away, double hXG, double aXG) {
        System.out.println("\n--- SCRIPT NỘI DUNG (H2FOOTBALL) ---");
        System.out.printf("Tiêu đề: Đại chiến nước Anh! %s có cản bước được %s?\n", home, away);
        System.out.println("Nội dung phân tích:");

        if (hXG > aXG) {
            System.out.printf("+ Dữ liệu xG cho thấy %s đang có lợi thế tấn công nhỉnh hơn với %.2f bàn kỳ vọng.\n", home, hXG);
        } else {
            System.out.printf("+ %s đang tỏ ra quá mạnh mẽ. Chỉ số xG dự báo đạt %.2f, hàng thủ %s cần cực kỳ cẩn trọng.\n", away, aXG, home);
        }

        System.out.println("+ Lời khuyên: Trận đấu có xu hướng " + (hXG + aXG > 2.5 ? "NỔ TÀI (Over)" : "XỈU (Under)") + " dựa trên hiệu suất tạo cơ hội của cả hai.");
    }
}
