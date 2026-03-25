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
                .findTop10ByTeamIdOrderByDateDesc(match.getHomeTeam().getId());
        List<PerformanceHistory> awayHistories = performanceHistoryRepository
                .findTop10ByTeamIdOrderByDateDesc(match.getAwayTeam().getId());

        if (CollectionUtils.isEmpty(homeHistories) || CollectionUtils.isEmpty(awayHistories)) {
            return prediction;
        }
        double expHomeGoals = Predictor.getExpectedXG(homeHistories);
        double expAwayGoals = Predictor.getExpectedXG(awayHistories);

        prediction.setHomeXG(expHomeGoals);
        prediction.setAwayXG(expAwayGoals);
        MatchOddsInsight matchOddsInsight = Predictor.calculateOddsInsight(expHomeGoals, expAwayGoals);
        prediction.setMatchOddsInsight(matchOddsInsight);
        return prediction;
    }

}
