package com.mmo.module.fb.crawler.mapper;

import com.mmo.converter.AbstractMapper;
import com.mmo.module.fb.channel.model.PredictionData;
import com.mmo.module.fb.entity.MatchPrediction;
import org.springframework.stereotype.Component;

@Component
public class MatchPrediction2PredictionDataMapper extends AbstractMapper<MatchPrediction, PredictionData> {

    @Override
    public PredictionData map(MatchPrediction source, PredictionData target) {
        target.setHomeTeam(source.getMatch().getHomeTeam().getName());
        target.setHomeTeamLogoUrl(source.getMatch().getHomeTeam().getLogoUrl());
        target.setAwayTeam(source.getMatch().getAwayTeam().getName());
        target.setAwayTeamLogoUrl(source.getMatch().getAwayTeam().getLogoUrl());
        target.setMatchTime(source.getMatchTime());

        target.setSofaHomeOdd(String.format("%.2f", source.getSofaHomeOdd() != null ? source.getSofaHomeOdd() : 0.0));
        target.setSofaDrawOdd(String.format("%.2f", source.getSofaDrawOdd() != null ? source.getSofaDrawOdd() : 0.0));
        target.setSofaAwayOdd(String.format("%.2f", source.getSofaAwayOdd() != null ? source.getSofaAwayOdd() : 0.0));
        target.setMarketHomeXG(String.format("%.2f", source.getMarketHomeXG() != null ? source.getMarketHomeXG() : 0.0));
        target.setMarketAwayXG(String.format("%.2f", source.getMarketAwayXG() != null ? source.getMarketAwayXG() : 0.0));
        target.setAwayProb(String.format("%.2f", source.getAwayProb() != null ? source.getAwayProb() * 100 : 0.0));
        target.setHomeProb(String.format("%.2f", source.getHomeProb() != null ? source.getHomeProb() * 100 : 0.0));
        target.setDrawProb(String.format("%.2f", source.getDrawProb() != null ? source.getDrawProb() * 100 : 0.0));

        target.setFairHomeOdd(String.format("%.2f", source.getFairHomeOdd() != null ? source.getFairHomeOdd() : 0.0));
        target.setFairDrawOdd(String.format("%.2f", source.getFairDrawOdd() != null ? source.getFairDrawOdd() : 0.0));
        target.setFairAwayOdd(String.format("%.2f", source.getFairAwayOdd() != null ? source.getFairAwayOdd() : 0.0));
        target.setHomeXG(String.format("%.2f", source.getExpectedHomeGoals() != null ? source.getExpectedHomeGoals() : 0.0));
        target.setAwayXG(String.format("%.2f", source.getExpectedAwayGoals() != null ? source.getExpectedAwayGoals() : 0.0));
        target.setH2HandicapMargin(source.getH2HandicapMargin());
        target.setH2TotalXG(String.format("%.2f", source.getH2TotalXG() != null ? source.getH2TotalXG() : 0.0));
        target.setH2ProbOver25(String.format("%.2f", source.getH2ProbOver25() != null ? source.getH2ProbOver25() : 0.0));
        target.setH2ProbUnder25(String.format("%.2f", source.getH2ProbUnder25() != null ? source.getH2ProbUnder25() : 0.0));
        target.setTopCorrectScores(source.getTopCorrectScores());

        target.setMostLikelyOutcome(source.getMostLikelyWinner());
        target.setValueBetPick(source.getValueBetPick());
        target.setSmartStakingSize(String.format("%.2f", source.getSmartStakingSize() != null ? source.getSmartStakingSize() : 0.0));
        target.setEdgePercentage(String.format("%.2f", source.getEdgePercentage() != null ? source.getEdgePercentage() : 0.0));

        return target;
    }
}
