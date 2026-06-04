package com.mmo.module.fb.channel.strategy;

import com.mmo.module.fb.channel.model.Platform;
import com.mmo.module.fb.entity.MatchPrediction;

import java.util.List;
import java.util.Map;

public interface ContentStrategy {

    String buildMatchesDashboardContent(List<MatchPrediction> freeMatches, List<MatchPrediction> vipMatches);

    String buildMatchesInsightsContent(List<MatchPrediction> matches);

    Platform getPlatform();

}
