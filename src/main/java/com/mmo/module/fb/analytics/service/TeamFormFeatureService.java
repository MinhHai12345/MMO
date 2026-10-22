package com.mmo.module.fb.analytics.service;

import com.mmo.module.fb.domain.entity.Match;

import java.time.Instant;
import java.util.Optional;

public interface TeamFormFeatureService {

    Optional<MatchFeatures> buildFeatures(Match targetMatch);

    /** Builds a point-in-time snapshot from already matured history, ordered newest first. */
    Optional<MatchFeatures> buildFeatures(Match targetMatch, java.util.List<Match> homeHistory,
                                          java.util.List<Match> awayHistory);

    record MatchFeatures(
            Long targetMatchId,
            Instant targetKickoff,
            TeamFeatures home,
            TeamFeatures away
    ) {
    }

    record TeamFeatures(
            Long teamId,
            int matchesUsed,
            int venueMatchesUsed,
            double averageGoalsFor,
            double averageGoalsAgainst,
            double averagePoints,
            double venueAverageGoalsFor,
            double venueAverageGoalsAgainst,
            double winRate,
            double cleanSheetRate,
            double scoredRate,
            Double averageXgFor,
            Double averageXgAgainst,
            String recentForm
    ) {
    }
}
