package com.mmo.module.fb.ingest.provider.footballdata.mapper;

import com.mmo.module.fb.ingest.provider.footballdata.dto.FootballDataDtos.AreaDto;
import com.mmo.module.fb.ingest.provider.footballdata.dto.FootballDataDtos.CompetitionDto;
import com.mmo.module.fb.ingest.provider.footballdata.dto.FootballDataDtos.SeasonDto;
import com.mmo.module.fb.ingest.provider.model.ProviderSeason;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FootballDataMapperTest {

    @Test
    void mapsAllProviderReturnedSeasonsUsingProviderIdsAndDates() {
        FootballDataMapper mapper = new FootballDataMapper();

        SeasonDto season2023 = new SeasonDto(501L, LocalDate.parse("2023-08-01"),
                LocalDate.parse("2024-05-31"), 38);
        SeasonDto season2024 = new SeasonDto(502L, LocalDate.parse("2024-08-01"),
                LocalDate.parse("2025-05-31"), 38);
        SeasonDto season2025 = new SeasonDto(503L, LocalDate.parse("2025-08-01"),
                LocalDate.parse("2026-05-31"), 38);
        CompetitionDto competition = new CompetitionDto(2021L, "Premier League", "PL", "LEAGUE",
                new AreaDto(2072L, "England", "ENG", null), null, season2025,
                List.of(season2023, season2024, season2025));

        var mapped = mapper.toLeague(competition);

        assertThat(mapped.seasons()).hasSize(3);
        assertThat(mapped.seasons()).extracting(ProviderSeason::externalId).containsExactly("501", "502", "503");
        assertThat(mapped.seasons()).extracting(ProviderSeason::year).containsExactly("2023", "2024", "2025");
        assertThat(mapped.seasons().get(0).startDate()).isEqualTo(LocalDate.parse("2023-08-01"));
        assertThat(mapped.seasons().get(2).current()).isTrue();
    }

    @Test
    void mapsHistoricalAndCurrentSeasonsWithoutConfigurationFilters() {
        FootballDataMapper mapper = new FootballDataMapper();
        SeasonDto historical = new SeasonDto(501L, LocalDate.parse("2023-08-01"),
                LocalDate.parse("2024-05-31"), 38);
        SeasonDto current = new SeasonDto(503L, LocalDate.parse("2025-08-01"),
                LocalDate.parse("2026-05-31"), 38);
        CompetitionDto competition = new CompetitionDto(2021L, "Premier League", "PL", "LEAGUE",
                null, null, current, List.of(historical, current));

        var mapped = mapper.toLeague(competition);

        assertThat(mapped.seasons()).hasSize(2);
        assertThat(mapped.seasons()).extracting(ProviderSeason::externalId).containsExactly("501", "503");
        assertThat(mapped.seasons()).extracting(ProviderSeason::year).containsExactly("2023", "2025");
    }
}
