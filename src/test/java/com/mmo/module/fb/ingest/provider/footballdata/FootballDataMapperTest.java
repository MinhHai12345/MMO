package com.mmo.module.fb.ingest.provider.footballdata;

import com.mmo.module.fb.domain.enums.StandingType;
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
    void mapsEachStandingTableTypeWithoutLosingScope() {
        TeamDto team = new TeamDto(65L, "Manchester City", "Man City", "MCI",
                null, 1880, "Etihad Stadium");
        StandingRowDto total = new StandingRowDto(
                1, team, 10, 8, 1, 1, 25, 30, 10, 20, "WWDWW", null);
        StandingRowDto home = new StandingRowDto(
                1, team, 5, 5, 0, 0, 15, 16, 2, 14, "WWWWW", null);
        StandingRowDto away = new StandingRowDto(
                2, team, 5, 3, 1, 1, 10, 14, 8, 6, "WWDLW", null);
        StandingsDto source = new StandingsDto(null, null, List.of(
                new StandingTableDto("REGULAR_SEASON", "TOTAL", "League A", List.of(total)),
                new StandingTableDto("REGULAR_SEASON", "HOME", "League A", List.of(home)),
                new StandingTableDto("REGULAR_SEASON", "AWAY", "League A", List.of(away))));

        var result = mapper.toStandings(source);

        assertEquals(3, result.size());
        assertEquals(List.of(StandingType.TOTAL, StandingType.HOME, StandingType.AWAY),
                result.stream().map(row -> row.standingType()).toList());
        assertEquals(25, result.get(0).points());
        assertEquals(15, result.get(1).points());
        assertEquals(10, result.get(2).points());
        assertEquals("League A", result.get(0).groupName());
        assertEquals(20, result.get(0).goalDifference());
    }

    @Test
    void defaultsUnknownStandingTableTypeToTotal() {
        TeamDto team = new TeamDto(65L, "Manchester City", "Man City", "MCI",
                null, 1880, "Etihad Stadium");
        StandingRowDto row = new StandingRowDto(
                1, team, 10, 8, 1, 1, 25, 30, 10, 20, "WWDWW", null);

        var result = mapper.toStandings(new StandingsDto(null, null,
                List.of(new StandingTableDto("REGULAR_SEASON", "UNKNOWN", null, List.of(row)))));

        assertEquals(StandingType.TOTAL, result.get(0).standingType());
    }
}
