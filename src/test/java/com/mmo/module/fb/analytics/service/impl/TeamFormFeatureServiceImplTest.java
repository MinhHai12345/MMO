package com.mmo.module.fb.analytics.service.impl;

import com.mmo.module.fb.domain.league.entity.League;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.team.entity.Team;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TeamFormFeatureServiceImplTest {

    private TeamFormFeatureServiceImpl service;
    private Team home;
    private Team away;
    private Match target;

    @BeforeEach
    void setUp() {
        service = new TeamFormFeatureServiceImpl(mock(
                com.mmo.module.fb.domain.match.repository.MatchRepository.class));
        home = team(1L, "Home");
        away = team(2L, "Away");
        League league = new League();
        league.setId(10L);
        Season season = new Season();
        season.setId(20L);
        season.setLeague(league);
        target = new Match();
        target.setId(30L);
        target.setHomeTeam(home);
        target.setAwayTeam(away);
        target.setSeason(season);
        target.setMatchTime(Instant.parse("2026-11-12T18:00:00Z"));
    }

    @Test
    void summarizesGoalsPointsRatesAndRecentFormFromNewestFirstHistory() {
        List<Match> homeHistory = List.of(
                finished(home, team(3L, "A"), 2, 0, 1.8, 0.4),
                finished(team(4L, "B"), home, 1, 1, 0.9, 1.1),
                finished(home, team(5L, "C"), 0, 1, 0.7, 1.2)
        );

        var result = service.buildFeatures(target, homeHistory, List.of()).orElseThrow();
        var features = result.home();

        assertEquals(3, features.matchesUsed());
        assertEquals(2, features.venueMatchesUsed());
        assertEquals(1.0, features.averageGoalsFor(), 1.0e-12);
        assertEquals(2.0 / 3.0, features.averageGoalsAgainst(), 1.0e-12);
        assertEquals(4.0 / 3.0, features.averagePoints(), 1.0e-12);
        assertEquals(1.0, features.venueAverageGoalsFor(), 1.0e-12);
        assertEquals(0.5, features.venueAverageGoalsAgainst(), 1.0e-12);
        assertEquals(1.0 / 3.0, features.winRate(), 1.0e-12);
        assertEquals(1.0 / 3.0, features.cleanSheetRate(), 1.0e-12);
        assertEquals(2.0 / 3.0, features.scoredRate(), 1.0e-12);
        assertEquals("WDL", features.recentForm());
    }

    @Test
    void calculatesExpectedGoalsOnlyFromMatchesWithBothValidValues() {
        List<Match> history = List.of(
                finished(home, team(3L, "A"), 2, 0, 1.8, 0.4),
                finished(home, team(4L, "B"), 1, 1, null, 1.1),
                finished(team(5L, "C"), home, 0, 1, 0.8, 0.9)
        );

        var features = service.buildFeatures(target, history, List.of()).orElseThrow().home();

        assertEquals(2, features.matchesUsed());
        assertEquals(1.3, features.averageXgFor(), 1.0e-12);
        assertEquals(0.65, features.averageXgAgainst(), 1.0e-12);
    }

    @Test
    void returnsNullExpectedGoalsWhenNoHistoryHasCompleteXgPair() {
        List<Match> history = List.of(
                finished(home, team(3L, "A"), 1, 0, 1.2, null),
                finished(home, team(4L, "B"), 1, 1, null, 1.1)
        );

        var features = service.buildFeatures(target, history, List.of()).orElseThrow().home();

        assertNull(features.averageXgFor());
        assertNull(features.averageXgAgainst());
    }

    @Test
    void venueHistoryUsesOnlyMatchesAtTheTargetVenue() {
        List<Match> homeHistory = List.of(
                finished(home, team(3L, "A"), 3, 0, 2.0, 0.2),
                finished(team(4L, "B"), home, 0, 2, 0.3, 1.5),
                finished(home, team(5L, "C"), 1, 1, 0.8, 0.8)
        );

        var features = service.buildFeatures(target, homeHistory, List.of()).orElseThrow().home();

        assertEquals(2, features.venueMatchesUsed());
        assertEquals(2.0, features.venueAverageGoalsFor(), 1.0e-12);
        assertEquals(0.5, features.venueAverageGoalsAgainst(), 1.0e-12);
    }

    @Test
    void handlesEmptyHistoryWithoutInventingRatesOrXg() {
        var features = service.buildFeatures(target, List.of(), List.of()).orElseThrow().home();

        assertEquals(0, features.matchesUsed());
        assertEquals(0, features.venueMatchesUsed());
        assertEquals(0.0, features.averageGoalsFor());
        assertEquals(0.0, features.winRate());
        assertEquals("", features.recentForm());
        assertNull(features.averageXgFor());
        assertNull(features.averageXgAgainst());
    }

    @Test
    void rejectsInvalidTargetFixture() {
        assertFalse(service.buildFeatures(null, List.of(), List.of()).isPresent());

        Match invalid = new Match();
        invalid.setMatchTime(target.getMatchTime());
        invalid.setHomeTeam(home);
        invalid.setAwayTeam(home);
        invalid.setSeason(target.getSeason());

        assertTrue(service.buildFeatures(invalid, List.of(), List.of()).isEmpty());
    }

    private static Team team(Long id, String name) {
        Team team = new Team();
        team.setId(id);
        team.setName(name);
        return team;
    }

    private static Match finished(Team home, Team away, int homeScore, int awayScore,
                                  Double homeXg, Double awayXg) {
        Match match = new Match();
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        match.setHomeScore(homeScore);
        match.setAwayScore(awayScore);
        match.setHomeXG(homeXg);
        match.setAwayXG(awayXg);
        return match;
    }
}
