package com.mmo.module.fb.core.service.impl;

import com.mmo.module.fb.core.engine.PoissonCalculatorEngine;
import com.mmo.module.fb.core.model.PredictionResult;
import com.mmo.module.fb.core.model.ScoreProbability;
import com.mmo.module.fb.core.service.PredictionEngineService;
import com.mmo.module.fb.provider.model.enums.Provider;
import com.mmo.module.fb.provider.model.sofa.SofaMatchesData;
import com.mmo.module.fb.provider.strategy.CrawlerStrategy;
import com.mmo.module.fb.provider.strategy.CrawlerStrategyRegistry;
import com.mmo.module.fb.entity.MatchPrediction;
import com.mmo.module.fb.entity.enums.MatchPredictionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionEngineServiceImpl implements PredictionEngineService {
    private final CrawlerStrategyRegistry crawlerStrategyRegistry;
    private final PoissonCalculatorEngine calculatorEngine;

    @Override
    public void calculateMatchPredict(MatchPrediction prediction) {
        try {
            Long homeTeamId = prediction.getMatch().getHomeTeam().getSofaScoreId();
            Long awayTeamId = prediction.getMatch().getAwayTeam().getSofaScoreId();

            CrawlerStrategy strategy = crawlerStrategyRegistry.getStrategy(Provider.SOFA_SCORE);
            List<SofaMatchesData.SofaEventDTO> homeHistories = strategy.getLatestHistoriesMatchesByTeamId(homeTeamId);
            List<SofaMatchesData.SofaEventDTO> awayHistories = strategy.getLatestHistoriesMatchesByTeamId(awayTeamId);

            if (homeHistories == null || homeHistories.isEmpty() || awayHistories == null || awayHistories.isEmpty()) {
                prediction.setStatus(MatchPredictionStatus.FAILED_NO_DATA);
                return;
            }

            // Execute Core Calculator Engine
            PredictionResult res = calculatorEngine.calculate(
                    homeHistories,
                    awayHistories,
                    homeTeamId,
                    awayTeamId,
                    prediction.getMatch().getHomeTeam().getRanking(),
                    prediction.getMatch().getAwayTeam().getRanking(),
                    prediction.getMarketHomeOdd(),
                    prediction.getMarketDrawOdd(),
                    prediction.getMarketAwayOdd()
            );

            // Map Result back to Entity
            prediction.setExpectedHomeXG(res.getExpectedHomeXG());
            prediction.setExpectedAwayXG(res.getExpectedAwayXG());
            prediction.setH2TotalXG(res.getH2TotalXG());
            prediction.setHandicapMargin(res.getHandicapMargin());

            prediction.setHomeWinProb(res.getHomeWinProb());
            prediction.setDrawProb(res.getDrawProb());
            prediction.setAwayWinProb(res.getAwayWinProb());

            prediction.setFairHomeOdd(res.getFairHomeOdd());
            prediction.setFairDrawOdd(res.getFairDrawOdd());
            prediction.setFairAwayOdd(res.getFairAwayOdd());

            prediction.setProbUnder25(res.getProbUnder25());
            prediction.setProbOver25(res.getProbOver25());

            String topScoresCSV = res.getTopCorrectScores().stream()
                    .map(ScoreProbability::getScore)
                    .collect(Collectors.joining(", "));
            prediction.setTopCorrectScores(topScoresCSV);

            prediction.setMarketHomeXG(res.getMarketHomeXG());
            prediction.setMarketAwayXG(res.getMarketAwayXG());

            prediction.setHasValue(res.isHasValue());
            prediction.setPremium(res.isPremium());
            prediction.setValueBetType(res.getValueBetType());
            prediction.setEdgePercentage(res.getEdgePercentage());
            prediction.setSmartStakingSize(res.getSmartStakingSize());

            prediction.setStatus(MatchPredictionStatus.CALCULATED);

        } catch (Exception e) {
            log.error("Failed to calculate prediction for Match ID: {}", prediction.getMatch().getId(), e);
            prediction.setStatus(MatchPredictionStatus.FAILED_NO_DATA);
        }
    }
}
