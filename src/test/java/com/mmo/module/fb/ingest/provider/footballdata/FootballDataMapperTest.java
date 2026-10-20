package com.mmo.module.fb.ingest.provider.footballdata;

import com.mmo.module.fb.ingest.provider.footballdata.dto.FootballDataDtos.*;
import com.mmo.module.fb.ingest.provider.footballdata.mapper.FootballDataMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FootballDataMapperTest {
    private final FootballDataMapper mapper = new FootballDataMapper();

    @Test
    void mapsCompetitionAndScopesSeasonExternalId() {
        CompetitionDto source = new CompetitionDto(
                2021L, "Premier League", "PL", "LEAGUE",
                new AreaDto(2072L, "England", "ENG", null), null,
                new SeasonDto(733L, LocalDate.parse("2025-08-15"),
                        LocalDate.parse("2026-05-24"), 8));

        var result = mapper.toLeague(source);

        assertEquals("2021", result.externalId());
        assertEquals("Premier League", result.name());
        assertEquals(1, result.seasons().size());
        assertEquals("2021:2025", result.seasons().get(0).externalId());
        assertEquals("2025", result.seasons().get(0).year());
    }

    @Test
    void mapsFixtureScoresAndKickoff() {
        TeamDto home = new TeamDto(65L, "Manchester City", "Man City", "MCI",
                null, 1880, "Etihad Stadium");
        TeamDto away = new TeamDto(64L, "Liverpool FC", "Liverpool", "LIV",
                null, 1892, "Anfield");
        MatchDto source = new MatchDto(
                500L, OffsetDateTime.parse("2025-09-01T15:00:00Z"), "FINISHED", 3,
                "REGULAR_SEASON", null, "Etihad Stadium", null, null, home, away,
                new ScoreDto("HOME_TEAM", "REGULAR",
                        new ScorePairDto(2, 1), new ScorePairDto(1, 0),
                        new ScorePairDto(2, 1), null, null));

        var result = mapper.toFixture(source);

        assertEquals("500", result.externalId());
        assertEquals("65", result.homeTeamExternalId());
        assertEquals("64", result.awayTeamExternalId());
        assertEquals(2, result.homeScore());
        assertEquals(1, result.awayScore());
        assertEquals("FINISHED", result.status());
        assertEquals(OffsetDateTime.parse("2025-09-01T15:00:00Z").toInstant(), result.kickoff());
    }

    @Test
    void mapsStandingRowsAndGroupName() {
        TeamDto team = new TeamDto(65L, "Manchester City", "Man City", "MCI",
                null, 1880, "Etihad Stadium");
        StandingRowDto row = new StandingRowDto(
                1, team, 10, 8, 1, 1, 25, 30, 10, 20, "WWDWW", null);
        StandingsDto source = new StandingsDto(null, null,
                List.of(new StandingTableDto("REGULAR_SEASON", "TOTAL", "League A", List.of(row))));

        var result = mapper.toStandings(source);

        assertEquals(1, result.size());
        assertEquals("65", result.get(0).teamExternalId());
        assertEquals(25, result.get(0).points());
        assertEquals("League A", result.get(0).groupName());
        assertEquals(20, result.get(0).goalDifference());
    }
}