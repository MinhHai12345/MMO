package com.mmo.module.fb.predict;

import com.mmo.module.fb.entity.PerformanceHistory;
import org.apache.commons.math3.distribution.PoissonDistribution;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Predictor {

    // 1. Tính toán xG kỳ vọng (Weighted Average)
    public static double getExpectedXG(List<PerformanceHistory> histories) {
        double sum = 0;
        double weights = 0;
        for (int i = 0; i < histories.size(); i++) {
            double w = i + 1;
            sum += histories.get(i).getXG() * w;
            weights += w;
        }
        return sum / weights;
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
