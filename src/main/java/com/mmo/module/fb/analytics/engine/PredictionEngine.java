package com.mmo.module.fb.analytics.engine;

import com.mmo.module.fb.match.entity.Match;
import com.mmo.module.fb.analytics.entity.MatchPrediction;

public interface PredictionEngine {
    MatchPrediction predict(Match match);
    String getEngineName();
    double getWeight();
}
