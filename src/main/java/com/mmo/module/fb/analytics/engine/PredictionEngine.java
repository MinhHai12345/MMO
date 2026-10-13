package com.mmo.module.fb.analytics.engine;

import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.MatchPrediction;

public interface PredictionEngine {
    MatchPrediction predict(Match match);
    String getEngineName();
    double getWeight();
}
