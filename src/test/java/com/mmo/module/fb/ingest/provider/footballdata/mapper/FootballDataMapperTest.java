package com.mmo.module.fb.ingest.provider.footballdata.mapper;

import com.mmo.module.fb.domain.league.enums.StandingType;
import com.mmo.module.fb.ingest.provider.footballdata.dto.FootballDataDtos.AreaDto;
import com.mmo.module.fb.ingest.provider.footballdata.dto.FootballDataDtos.CompetitionDto;
import com.mmo.module.fb.ingest.provider.footballdata.dto.FootballDataDtos.SeasonDto;
import com.mmo.module.fb.ingest.provider.footballdata.dto.FootballDataDtos.StandingRowDto;
import com.mmo.module.fb.ingest.provider.footballdata.dto.FootballDataDtos.StandingTableDto;
import com.mmo.module.fb.ingest.provider.footballdata.dto.FootballDataDtos.StandingsDto;
import com.mmo.module.fb.ingest.provider.footballdata.dto.FootballDataDtos.TeamDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FootballDataMapperTest {

    @Test
    void exposesFreeTierSeasonsFrom2023ThroughCurrentSeason() {
        List<SeasonDto> seasons = java.util.stream.IntStream.rangeClosed(2014, 2026)
                .mapToObj(year -> new SeasonDto((long) year,
                        LocalDate.of(year, 7, 1), LocalDate.of(year + 1, 6, 30), 1))
                .toList();
        SeasonDto current = seasons.get(seasons.size() - 1);
        CompetitionDto competition = new CompetitionDto(
                2021L, "Premier League", "PL", "LEAGUE",
                new AreaDto(2072L, "England", "ENG", null),
                null, current, seasons);

        var mapped = new FootballDataMapper().toLeague(competition);

        assertEquals(List.of("2023", "2024", "2025", "2026"),
                mapped.seasons().stream().map(season -> season.year()).toList());
        assertEquals("2026", mapped.seasons().get(3).year());
        org.junit.jupiter.api.Assertions.assertTrue(mapped.seasons().get(3).current());
    }

    @Test
    void mapsTotalHomeAwayAndGroupTablesWithoutLosingTheirDimensions() {
        FootballDataMapper mapper = new FootballDataMapper();
        TeamDto team = new TeamDto(57L, "Arsenal FC", "Arsenal", "ARS", null, 1886, null);
        StandingRowDto row = new StandingRowDto(1, team, 10, 8, 1, 1, 25,
                22, 8, 14, "WWDWW", null);
        StandingsDto response = new StandingsDto(null, null, List.of(
                new StandingTableDto("REGULAR_SEASON", "TOTAL", null, List.of(row)),
                new StandingTableDto("REGULAR_SEASON", "HOME", null, List.of(row)),
                new StandingTableDto("GROUP_STAGE", "TOTAL", "Group A", List.of(row))));

        var mapped = mapper.toStandings(response);

        assertEquals(3, mapped.size());
        assertEquals(StandingType.TOTAL, mapped.get(0).standingType());
        assertEquals(StandingType.HOME, mapped.get(1).standingType());
        assertEquals(StandingType.TOTAL, mapped.get(2).standingType());
        assertEquals("Group A", mapped.get(2).groupName());
    }

    @Test
    void rejectsUnknownProviderStandingTypeInsteadOfTreatingItAsTotal() {
        assertThrows(IllegalArgumentException.class, () ->
                StandingType.fromProviderValue("SPLIT"));
    }

    @Test
    void defaultsMissingProviderStandingTypeToTotal() {
        assertEquals(StandingType.TOTAL, StandingType.fromProviderValue(" "));
    }
}
