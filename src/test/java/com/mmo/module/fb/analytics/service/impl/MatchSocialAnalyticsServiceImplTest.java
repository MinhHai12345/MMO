package com.mmo.module.fb.analytics.service.impl;

import com.mmo.module.fb.analytics.service.TeamFormFeatureService;
import com.mmo.module.fb.domain.entity.League;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.Season;
import com.mmo.module.fb.domain.entity.Team;
import com.mmo.module.fb.domain.repository.LeagueStandingRepository;
import com.mmo.module.fb.domain.repository.MatchOddsRepository;
import com.mmo.module.fb.domain.repository.MatchPredictionRepository;
import com.mmo.module.fb.domain.repository.MatchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MatchSocialAnalyticsServiceImplTest {

    private final MatchRepository matchRepository = mock(MatchRepository.class);
    private final MatchPredictionRepository predictionRepository = mock(MatchPredictionRepository.class);
    private final TeamFormFeatureService featureService = mock(TeamFormFeatureService.class);
    private final LeagueStandingRepository standingRepository = mock(LeagueStandingRepository.class);
    private final MatchOddsRepository oddsRepository = mock(MatchOddsRepository.class);

    private final MatchSocialAnalyticsServiceImpl service = new MatchSocialAnalyticsServiceImpl(
            matchRepository, predictionRepository, featureService, standingRepository, oddsRepository);

    @Test
    void rejectsUnknownMatch() {
        when(matchRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.analyze(999L));
    }

    @Test
    void returnsWarningsInsteadOfInventingMissingAnalytics() {
        Match match = mock(Match.class);
        Team home = team(1L, "Home FC");
        Team away = team(2L, "Away FC");
        League league = mock(League.class);
        Season season = mock(Season.class);

        when(matchRepository.findById(10L)).thenReturn(Optional.of(match));
        when(match.getId()).thenReturn(10L);
        when(match.getHomeTeam()).thenReturn(home);
        when(match.getAwayTeam()).thenReturn(away);
        when(match.getSeason()).thenReturn(season);
        when(season.getId()).thenReturn(7L);
        when(season.getYear()).thenReturn("2026");
        when(season.getLeague()).thenReturn(league);
        when(league.getName()).thenReturn("Test League");
        when(match.getMatchTime()).thenReturn(Instant.parse("2026-10-21T12:00:00Z"));
        when(match.getStatus()).thenReturn(com.mmo.module.fb.domain.enums.MatchStatus.SCHEDULED);
        when(featureService.buildFeatures(match)).thenReturn(Optional.empty());
        when(standingRepository.findBySeasonIdAndTeamIdAndStandingType(anyLong(), anyLong(), any()))
                .thenReturn(Optional.empty());
        when(predictionRepository.findByMatchId(10L)).thenReturn(Optional.empty());
        when(oddsRepository.findAllByMatchId(10L)).thenReturn(java.util.List.of());

        var insight = service.analyze(10L);

        assertEquals(10L, insight.matchId());
        assertEquals("Test League", insight.competition());
        assertNull(insight.prediction());
        assertNull(insight.market());
        assertTrue(insight.dataWarnings().stream().anyMatch(w -> w.contains("historical data")));
        assertTrue(insight.dataWarnings().stream().anyMatch(w -> w.contains("probabilities")));
        assertTrue(insight.dataWarnings().stream().anyMatch(w -> w.contains("odds")));
    }

    private Team team(Long id, String name) {
        Team team = mock(Team.class);
        when(team.getId()).thenReturn(id);
        when(team.getName()).thenReturn(name);
        return team;
    }
}
