package com.mmo.module.fb.core.service;

import com.mmo.module.fb.entity.MatchPrediction;

public interface PredictionEngineService {

    void calculateMatchPredict(MatchPrediction prediction);
}
