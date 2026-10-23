package com.mmo.module.fb.analytics.service;

import com.mmo.module.fb.match.entity.Match;

import java.util.Optional;

public interface MatchHistoryAnalyticsService {

    Optional<ExpectedGoals> estimateFromGoals(Match match);

    Optional<ExpectedGoals> estimateFromExpectedGoals(Match match);

    record ExpectedGoals(double home, double away, int homeSamples, int awaySamples) {
    }
}
