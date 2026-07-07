package com.mmo.module.fb.channel.strategy;

import com.mmo.module.fb.channel.model.Platform;
import com.mmo.module.fb.channel.model.PredictionData;
import com.mmo.module.fb.entity.MatchPrediction;

import java.util.List;

public interface ContentStrategy {

    String buildMatchesDashboardContent(List<MatchPrediction> freeMatches, List<MatchPrediction> vipMatches);

    String buildMatchesInsightsContent(List<PredictionData> matches);

    byte[] buildMatchesInsightImage(PredictionData match);

    String buildMatchesRecapContent(List<MatchPrediction> matches);

    Platform getPlatform();

}
