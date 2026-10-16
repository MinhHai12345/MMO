package com.mmo.module.fb.analytics.service;

import com.mmo.module.fb.domain.entity.League;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.Season;
import com.mmo.module.fb.domain.entity.Team;
import com.mmo.module.fb.domain.enums.MatchStatus;
import com.mmo.module.fb.domain.repository.MatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MatchHistoryAnalyticsServiceTest {

    private MatchRepository matchRepository;
    private MatchHistoryAnalyticsService analyticsService;
    private Team homeTeam;
    private Team awayTeam;
    private Season season;

    @BeforeEach
    void setUp() {
        matchRepository = mock(MatchRepository.class);
        analyticsService = new MatchHistoryAnalyticsService(matchRepository);

        homeTeam = team(1L, "Home");
        awayTeam = team(2L, "Away");
        League league = League.builder().name("Test League").slug("test-league").active(true).build();
        ReflectionTestUtils.setField(league, "id", 9L);
        season = Season.builder().league(league).year("2026").build();
        ReflectionTestUtils.setField(season, "id", 10L);
    }

    @Test
    void estimatesGoalsFromRecentSameLeagueResultsWithVenueSplits() {
        List<Match> homeHistory = List.of(
                historyMatch(homeTeam, team(3L, "Opponent 1"), 3, 1, 2.1, 0.5),
                historyMatch(homeTeam, team(4L, "Opponent 2"), 2, 0, 1.4, 0.2),
                historyMatch(homeTeam, team(5L, "Opponent 3"), 1, 0, 0.9, 0.3)
        );
        List<Match> awayHistory = List.of(
                historyMatch(team(6L, "Opponent 4"), awayTeam, 1, 2, 1.0, 1.8),
                historyMatch(team(7L, "Opponent 5"), awayTeam, 2, 1, 1.7, 1.2),
                historyMatch(team(8L, "Opponent 6"), awayTeam, 0, 1, 0.6, 1.1)
        );
        when(matchRepository.findRecentFinishedMatchesForTeamInLeague(
                eq(1L), eq(9L), eq(MatchStatus.FINISHED), any(Pageable.class))).thenReturn(homeHistory);
        when(matchRepository.findRecentFinishedMatchesForTeamInLeague(
                eq(2L), eq(9L), eq(MatchStatus.FINISHED), any(Pageable.class))).thenReturn(awayHistory);
        when(matchRepository.findRecentFinishedMatchesInLeague(
                eq(9L), eq(MatchStatus.FINISHED), any(Pageable.class))).thenReturn(leagueBaseline());

        var result = analyticsService.estimateFromGoals(upcomingMatch()).orElseThrow();

        assertTrue(result.home() > 0);
        assertTrue(result.away() > 0);
        assertTrue(result.home() <= 4.5);
        assertTrue(result.away() <= 4.5);
        verify(matchRepository).findRecentFinishedMatchesForTeamInLeague(
                eq(1L), eq(9L), eq(MatchStatus.FINISHED), any(Pageable.class));
        verify(matchRepository).findRecentFinishedMatchesForTeamInLeague(
                eq(2L), eq(9L), eq(MatchStatus.FINISHED), any(Pageable.class));
    }

    @Test
    void estimatesExpectedGoalsOnlyWhenTeamAndLeagueXgCoverageExists() {
        List<Match> homeHistory = List.of(
                historyMatch(homeTeam, team(3L, "Opponent 1"), 3, 1, 2.1, 0.5),
                historyMatch(homeTeam, team(4L, "Opponent 2"), 2, 0, 1.4, 0.2),
                historyMatch(homeTeam, team(5L, "Opponent 3"), 1, 0, 0.9, 0.3)
        );
        List<Match> awayHistory = List.of(
                historyMatch(team(6L, "Opponent 4"), awayTeam, 1, 2, 1.0, 1.8),
                historyMatch(team(7L, "Opponent 5"), awayTeam, 2, 1, 1.7, 1.2),
                historyMatch(team(8L, "Opponent 6"), awayTeam, 0, 1, 0.6, 1.1)
        );
        when(matchRepository.findRecentFinishedMatchesForTeamInLeague(
                eq(1L), eq(9L), eq(MatchStatus.FINISHED), any(Pageable.class))).thenReturn(homeHistory);
        when(matchRepository.findRecentFinishedMatchesForTeamInLeague(
                eq(2L), eq(9L), eq(MatchStatus.FINISHED), any(Pageable.class))).thenReturn(awayHistory);
        when(matchRepository.findRecentFinishedMatchesInLeague(
                eq(9L), eq(MatchStatus.FINISHED), any(Pageable.class))).thenReturn(leagueBaseline());

        var result = analyticsService.estimateFromExpectedGoals(upcomingMatch()).orElseThrow();

        assertTrue(result.home() > 0);
        assertTrue(result.away() > 0);
        assertTrue(result.homeSamples() >= 3);
        assertTrue(result.awaySamples() >= 3);
    }

    @Test
    void xgModelDoesNotInventValuesWhenHistoricalXgIsMissing() {
        List<Match> homeHistory = List.of(
                historyMatch(homeTeam, team(3L, "Opponent 1"), 2, 1, null, null),
                historyMatch(homeTeam, team(4L, "Opponent 2"), 1, 0, null, null),
                historyMatch(homeTeam, team(5L, "Opponent 3"), 1, 1, null, null)
        );
        List<Match> awayHistory = List.of(
                historyMatch(team(6L, "Opponent 4"), awayTeam, 1, 2, null, null),
                historyMatch(team(7L, "Opponent 5"), awayTeam, 2, 1, null, null),
                historyMatch(team(8L, "Opponent 6"), awayTeam, 0, 1, null, null)
        );
        when(matchRepository.findRecentFinishedMatchesForTeamInLeague(
                eq(1L), eq(9L), eq(MatchStatus.FINISHED), any(Pageable.class))).thenReturn(homeHistory);
        when(matchRepository.findRecentFinishedMatchesForTeamInLeague(
                eq(2L), eq(9L), eq(MatchStatus.FINISHED), any(Pageable.class))).thenReturn(awayHistory);

        assertTrue(analyticsService.estimateFromExpectedGoals(upcomingMatch()).isEmpty());
    }

    private List<Match> leagueBaseline() {
        return List.of(
                historyMatch(team(30L, "League 1"), team(31L, "League 2"), 2, 1, 1.8, 0.9),
                historyMatch(team(32L, "League 3"), team(33L, "League 4"), 1, 0, 1.2, 0.4),
                historyMatch(team(34L, "League 5"), team(35L, "League 6"), 3, 2, 2.5, 1.4),
                historyMatch(team(36L, "League 7"), team(37L, "League 8"), 0, 1, 0.6, 1.1),
                historyMatch(team(38L, "League 9"), team(39L, "League 10"), 2, 2, 1.7, 1.5),
                historyMatch(team(40L, "League 11"), team(41L, "League 12"), 1, 2, 1.0, 1.6),
                historyMatch(team(42L, "League 13"), team(43L, "League 14"), 2, 0, 1.9, 0.5),
                historyMatch(team(44L, "League 15"), team(45L, "League 16"), 0, 0, 0.3, 0.2),
                historyMatch(team(46L, "League 17"), team(47L, "League 18"), 3, 1, 2.2, 0.7),
                historyMatch(team(48L, "League 19"), team(49L, "League 20"), 1, 1, 1.1, 1.0)
        );
    }

    private Match upcomingMatch() {
        return Match.builder()
                .season(season)
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .status(MatchStatus.SCHEDULED)
                .build();
    }

    private Match historyMatch(Team home, Team away, Integer homeScore, Integer awayScore,
                               Double homeXg, Double awayXg) {
        return Match.builder()
                .season(season)
                .homeTeam(home)
                .awayTeam(away)
                .homeScore(homeScore)
                .awayScore(awayScore)
                .homeXG(homeXg)
                .awayXG(awayXg)
                .status(MatchStatus.FINISHED)
                .build();
    }

    private Team team(Long id, String name) {
        Team team = Team.builder().name(name).build();
        ReflectionTestUtils.setField(team, "id", id);
        return team;
    }
}
