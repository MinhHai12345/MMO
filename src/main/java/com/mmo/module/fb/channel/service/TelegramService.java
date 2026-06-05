package com.mmo.module.fb.channel.service;

import com.mmo.module.fb.entity.MatchPrediction;

import java.util.List;

public interface TelegramService {
    void notifyMatchesDashboard(List<MatchPrediction> freeMatches, List<MatchPrediction> vipMatches);

    void notifyMatchesInsights(List<MatchPrediction> matches);

    void notifyMatchesRecap(List<MatchPrediction> matches);

}
