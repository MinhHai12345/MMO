package com.mmo.module.fb.ingest.adapter.out.provider.footballdata.mapper;

import com.mmo.module.fb.ingest.adapter.out.provider.footballdata.dto.FootballDataDtos.AreaDto;
import com.mmo.module.fb.ingest.adapter.out.provider.footballdata.dto.FootballDataDtos.CompetitionDto;
import com.mmo.module.fb.ingest.adapter.out.provider.footballdata.dto.FootballDataDtos.SeasonDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FootballDataResponseMapperTest {

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

        var mapped = new FootballDataResponseMapper().toLeague(competition);

        assertEquals(List.of("2023", "2024", "2025", "2026"),
                mapped.seasons().stream().map(season -> season.year()).toList());
        assertEquals("2026", mapped.seasons().get(3).year());
        org.junit.jupiter.api.Assertions.assertTrue(mapped.seasons().get(3).current());
    }
}
