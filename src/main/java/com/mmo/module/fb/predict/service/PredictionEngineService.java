package com.mmo.module.fb.predict.service;

import com.mmo.module.fb.entity.MatchPrediction;

public interface PredictionEngineService {

    void calculateMatchPredict(MatchPrediction prediction);
}
