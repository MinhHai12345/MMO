package com.mmo.module.fb.analytics.engine;

import com.mmo.module.fb.analytics.service.TeamFormFeatureService;
import com.mmo.module.fb.analytics.service.TeamFormFeatureService.MatchFeatures;
import com.mmo.module.fb.analytics.service.TeamFormFeatureService.TeamFeatures;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.MatchPrediction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeamFormPredictionEngineTest {

    private final TeamFormFeatureService featureService = mock(TeamFormFeatureService.class);
    private final TeamFormPredictionEngine engine = new TeamFormPredictionEngine(featureService);
    private final Match match = mock(Match.class);

    @Test
    void returnsNullWhenFeatureSnapshotIsUnavailable() {
        when(featureService.buildFeatures(match)).thenReturn(Optional.empty());

        assertNull(engine.predict(match));
    }

    @Test
    void returnsNullWhenEitherTeamHasInsufficientHistory() {
        when(featureService.buildFeatures(match)).thenReturn(Optional.of(features(
                team(4, 2, 1.4, 1.1, 1.2, 1.0),
                team(8, 4, 1.6, 1.0, 1.5, 0.9))));

        assertNull(engine.predict(match));
    }

    @Test
    void createsValidGoalBasedPredictionWhenHistoryIsSufficient() {
        when(featureService.buildFeatures(match)).thenReturn(Optional.of(features(
                team(8, 4, 1.8, 0.9, 2.0, 0.8),
                team(9, 5, 1.3, 1.4, 1.1, 1.6))));

        MatchPrediction prediction = engine.predict(match);

        assertNotNull(prediction);
        assertEquals("TEAM_FORM", engine.getEngineName());
        assertEquals("PENDING", prediction.getPredictionStatus());
        assertNull(prediction.getPredictedHomeXg());
        assertNull(prediction.getPredictedAwayXg());
        assertNotNull(prediction.getWinHomeProb());
        assertNotNull(prediction.getDrawProb());
        assertNotNull(prediction.getWinAwayProb());

        BigDecimal total = prediction.getWinHomeProb()
                .add(prediction.getDrawProb())
                .add(prediction.getWinAwayProb());
        assertTrue(total.subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.00001")) <= 0);
        assertTrue(prediction.getPredictionReasoning().contains("Team-form Poisson model"));
        assertEquals(0.20, engine.getWeight(), 0.000001);
    }

    private MatchFeatures features(TeamFeatures home, TeamFeatures away) {
        return new MatchFeatures(1L, Instant.parse("2026-10-21T10:00:00Z"), home, away);
    }

    private TeamFeatures team(int matches, int venueMatches, double goalsFor, double goalsAgainst,
                              double venueGoalsFor, double venueGoalsAgainst) {
        return new TeamFeatures(1L, matches, venueMatches, goalsFor, goalsAgainst, 1.4,
                venueGoalsFor, venueGoalsAgainst, 0.4, 0.3, 0.7, null, null, "WDLWW");
    }
}
