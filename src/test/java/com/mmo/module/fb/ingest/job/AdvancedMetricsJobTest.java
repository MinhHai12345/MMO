package com.mmo.module.fb.ingest.job;

import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.Team;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdvancedMetricsJobTest {

    @Test
    void calculateFormDataUsesTeamPerspectiveForHomeAndAwayFixtures() {
        Team team = team(1L, "Team A");
        Team opponent = team(2L, "Team B");

        List<Match> matches = List.of(
                match(team, opponent, 2, 0),
                match(opponent, team, 1, 1),
                match(opponent, team, 3, 1),
                match(team, opponent, 0, 2)
        );

        assertEquals("WDLL", AdvancedMetricsJob.calculateFormData(matches, team));
    }

    @Test
    void calculateFormDataSkipsMatchesWithoutCompleteScores() {
        Team team = team(1L, "Team A");
        Team opponent = team(2L, "Team B");

        List<Match> matches = List.of(
                match(team, opponent, 2, 0),
                match(team, opponent, null, 0),
                match(opponent, team, 0, 1)
        );

        assertEquals("WW", AdvancedMetricsJob.calculateFormData(matches, team));
    }

    private static Team team(Long id, String name) {
        Team team = Team.builder().name(name).build();
        ReflectionTestUtils.setField(team, "id", id);
        return team;
    }

    private static Match match(Team home, Team away, Integer homeScore, Integer awayScore) {
        return Match.builder()
                .homeTeam(home)
                .awayTeam(away)
                .homeScore(homeScore)
                .awayScore(awayScore)
                .build();
    }
}
