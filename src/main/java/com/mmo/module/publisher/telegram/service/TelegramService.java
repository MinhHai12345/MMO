package com.mmo.module.publisher.telegram.service;

import com.mmo.module.fb.publisher.model.PredictionData;
import com.mmo.module.fb.entity.MatchPrediction;

import java.util.List;

public interface TelegramService {
    void notifyMatchesDashboard(List<MatchPrediction> freeMatches, List<MatchPrediction> vipMatches);

    void notifyMatchesInsights(List<PredictionData> matches);

    void notifyMatchesRecap(List<MatchPrediction> matches);

}
