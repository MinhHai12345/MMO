package com.mmo.module.fb.predict.service.impl;

import com.mmo.module.fb.entity.Match;
import com.mmo.module.fb.entity.PerformanceHistory;
import com.mmo.module.fb.model.MatchOddsInsight;
import com.mmo.module.fb.model.MatchPrediction;
import com.mmo.module.fb.predict.Predictor;
import com.mmo.module.fb.predict.service.PredictService;
import com.mmo.module.fb.repository.PerformanceHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PredictServiceImpl implements PredictService {
    private final PerformanceHistoryRepository performanceHistoryRepository;

    @Override
    public MatchPrediction predict(Match match) {
        MatchPrediction prediction = new MatchPrediction();
        prediction.setLeague(match.getLeague().getName());
        prediction.setHomeName(match.getHomeTeam().getName());
        prediction.setAwayName(match.getAwayTeam().getName());
        prediction.setMatchTime(match.getMatchTime().toString());

        List<PerformanceHistory> homeHistories = performanceHistoryRepository
                .findTop5ByTeamIdOrderByDateDesc(match.getHomeTeam().getId());
        List<PerformanceHistory> awayHistories = performanceHistoryRepository
                .findTop5ByTeamIdOrderByDateDesc(match.getAwayTeam().getId());

        if (CollectionUtils.isEmpty(homeHistories) || CollectionUtils.isEmpty(awayHistories)) {
            return prediction;
        }
        double expHomeGoals = Predictor.getExpectedXG(homeHistories);
        double expAwayGoals = Predictor.getExpectedXG(awayHistories);

        prediction.setHomeXG(expHomeGoals);
        prediction.setAwayXG(expAwayGoals);
        prediction.setHomeTeamForm(buildTeamForm(homeHistories));
        prediction.setAwayTeamForm(buildTeamForm(awayHistories));
        prediction.setHomeFormStable(isFormStable(homeHistories.stream()
                .map(PerformanceHistory::getXG).collect(Collectors.toList())));
        prediction.setAwayFormStable(isFormStable(awayHistories.stream()
                .map(PerformanceHistory::getXG).collect(Collectors.toList())));

        MatchOddsInsight matchOddsInsight = Predictor.calculateOddsInsight(expHomeGoals, expAwayGoals);
        prediction.setMatchOddsInsight(matchOddsInsight);

        prediction.setConfidence(calculateConfidence(prediction));
        return prediction;
    }

    private String buildTeamForm(List<PerformanceHistory> histories) {
        return histories.stream()
                .map(it -> it.getResult().toUpperCase())
                .collect(Collectors.joining(" - "));
    }

    public boolean isFormStable(List<Double> lastXGs) {
        if (lastXGs == null || lastXGs.size() < 5) return false;

        double mean = lastXGs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = lastXGs.stream()
                .mapToDouble(x -> Math.pow(x - mean, 2))
                .average().orElse(0.0);

        double standardDeviation = Math.sqrt(variance);
        return standardDeviation < 0.6;
    }

    public int calculateConfidence(MatchPrediction p) {
        double score = 0;

        // 1. Poisson Dominance (Max 3.0 pts)
        // Nếu xác suất thắng cao (>60%), cộng điểm lớn
        double maxProb = Math.max(p.getMatchOddsInsight().getHomeOdds(), p.getMatchOddsInsight().getAwayOdds());
        if (maxProb > 0.70) score += 3.0;
        else if (maxProb > 0.60) score += 2.5;
        else if (maxProb > 0.50) score += 2.0;
        else score += 1.0;

        // 2. xG Margin (Max 3.0 pts)
        // Khoảng cách xG giữa 2 đội càng lớn, dự đoán càng rõ ràng
        double xGDiff = Math.abs(p.getHomeXG() - p.getAwayXG());
        if (xGDiff > 1.0) score += 3.0;
        else if (xGDiff > 0.5) score += 2.0;
        else score += 1.0;

        // 3. Form Stability (Max 2.0 pts)
        // Giả sử bạn tính được độ lệch chuẩn xG của 5 trận gần nhất
        // Nếu ổn định (low variance) -> Cộng điểm
        if (p.isHomeFormStable() && p.isAwayFormStable()) score += 2.0;
        else score += 1.0;

        return (int) Math.round(score + 1.0);
    }

}
