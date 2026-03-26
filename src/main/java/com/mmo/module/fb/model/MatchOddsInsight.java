package com.mmo.module.fb.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@Builder
public class MatchOddsInsight {
    private double homeOdds;
    private double drawOdds;
    private double awayOdds;
    private double over25Odds;
    private Map<String, Double> scoreMap;

    // --- Logic bổ trợ để "Fullfill" nội dung Post ---

    /**
     * Lấy danh sách 3 tỷ số có xác suất cao nhất
     * Ví dụ: ["2-0 (12.1%)", "1-0 (11.6%)", "2-1 (9.7%)"]
     */
    public List<String> getTopCorrectScores(int limit) {
        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(e -> String.format("%s (%.1f%%)", e.getKey(), e.getValue() * 100))
                .collect(Collectors.toList());
    }

    public String buildScoreContent() {
        StringBuilder sb = new StringBuilder();

        scoreMap.entrySet()
                .stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(3)
                .forEach(e -> {
                    sb.append(" ")
                            .append(e.getKey())
                            .append(" (")
                            .append(Math.round(e.getValue() * 100))
                            .append("%)\n");
                });

        return sb.toString();
    }


    /**
     * Xác định kèo sáng nhất dựa trên xác suất cao nhất
     */
    public String getMainVerdict() {
        if (homeOdds > 0.6) return "HOME DOMINANCE";
        if (awayOdds > 0.6) return "AWAY DOMINANCE";
        if (over25Odds > 0.65) return "GOAL FEST (OVER 2.5)";
        if (drawOdds > 0.3) return "POTENTIAL DRAW";
        return "CLOSE MATCH";
    }

    /**
     * Trả về xác suất dưới dạng phần trăm đẹp mắt cho UI/Bot
     */
    public String getFormattedHomeWin() {
        return String.format("%.1f%%", homeOdds * 100);
    }
}
