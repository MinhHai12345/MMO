package com.mmo.module.fb.analytics.engine;

import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.MatchPrediction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Component
public class StatisticalPredictionEngine implements PredictionEngine {

    @Override
    public MatchPrediction predict(Match match) {
        log.info("🔮 Running statistical prediction for match {}", match.getId());

        // TODO: Implement statistical prediction logic
        // This would use:
        // - Head-to-head records
        // - Team form (last 5-10 matches)
        // - League position
        // - Home/away records

        double homeWinProb = 0.45;
        double drawProb = 0.28;
        double awayWinProb = 0.27;

        return MatchPrediction.builder()
                .match(match)
                .predictedHomeXg(BigDecimal.valueOf(1.4).setScale(2, RoundingMode.HALF_UP))
                .predictedAwayXg(BigDecimal.valueOf(1.1).setScale(2, RoundingMode.HALF_UP))
                .winHomeProb(BigDecimal.valueOf(homeWinProb).setScale(4, RoundingMode.HALF_UP))
                .drawProb(BigDecimal.valueOf(drawProb).setScale(4, RoundingMode.HALF_UP))
                .winAwayProb(BigDecimal.valueOf(awayWinProb).setScale(4, RoundingMode.HALF_UP))
                .predictedResult(determineResult(homeWinProb, drawProb, awayWinProb))
                .predictionStatus("PENDING")
                .confidenceScore(BigDecimal.valueOf(Math.max(homeWinProb, Math.max(drawProb, awayWinProb))).setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    @Override
    public String getEngineName() {
        return "STATISTICAL";
    }

    @Override
    public double getWeight() {
        return 0.3;
    }

    private String determineResult(double homeProb, double drawProb, double awayProb) {
        if (homeProb > drawProb && homeProb > awayProb) return "HOME";
        if (awayProb > drawProb && awayProb > homeProb) return "AWAY";
        return "DRAW";
    }
}
