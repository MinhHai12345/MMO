package com.mmo.module.fb.ingest.provider.understats.mapper;

import com.mmo.module.fb.ingest.provider.model.ProviderLeague;
import com.mmo.module.fb.ingest.provider.understats.dto.UnderStatDtos;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class UnderStatLeagueMapperTest {
    private final UnderStatLeagueMapper mapper = new UnderStatLeagueMapper();

    @Test
    void mapsLeagueMetadataAndCompleteHistoricalSeasonRange() {
        UnderStatDtos.StatDataResponse response = new UnderStatDtos.StatDataResponse(List.of(
                stat("1", "EPL", "2024", "8"),
                stat("1", "EPL", "2025", "8"),
                stat("2", "La Liga", "2025", "8"),
                stat("3", "Bundesliga", "2025", "8")
        ));

        List<ProviderLeague> leagues = mapper.toProviderLeagues(response);
        int currentSeasonStartYear = currentSeasonStartYear();
        List<String> expectedYears = IntStream.rangeClosed(2014, currentSeasonStartYear)
                .mapToObj(String::valueOf).toList();

        assertThat(leagues).hasSize(3);
        ProviderLeague epl = leagues.get(0);
        assertThat(epl.externalId()).isEqualTo("1");
        assertThat(epl.identify()).isEqualTo("EPL");
        assertThat(epl.name()).isEqualTo("Premier League");
        assertThat(epl.country()).isEqualTo("England");
        assertThat(epl.seasons()).extracting("externalId")
                .containsExactlyElementsOf(expectedYears.stream().map(year -> "1:" + year).toList());
        assertThat(epl.seasons()).extracting("year").containsExactlyElementsOf(expectedYears);

        ProviderLeague laLiga = leagues.get(1);
        assertThat(laLiga.identify()).isEqualTo("La_Liga");
        assertThat(laLiga.name()).isEqualTo("LaLiga");
        assertThat(laLiga.seasons()).extracting("year").containsExactlyElementsOf(expectedYears);
        assertThat(mapper.toProviderSeasons(response, "1")).extracting("year")
                .containsExactlyElementsOf(expectedYears);
        assertThat(mapper.toProviderSeasons(response, "2")).hasSize(expectedYears.size());
    }

    @Test
    void doesNotDependOnSparseMonthlyCatalogueRowsForHistoricalSeasons() {
        UnderStatDtos.StatDataResponse response = new UnderStatDtos.StatDataResponse(List.of(
                stat("1", "EPL", "2014", "8"),
                stat("1", "EPL", "2023", "8")
        ));

        var seasons = mapper.toProviderSeasons(response, "1");

        assertThat(seasons).hasSize(currentSeasonStartYear() - 2014 + 1);
        assertThat(seasons).extracting("year").first().isEqualTo("2014");
        assertThat(seasons).extracting("year").contains("2023");
        assertThat(seasons).extracting("year").last().isEqualTo(String.valueOf(currentSeasonStartYear()));
        assertThat(seasons).extracting("externalId").contains("1:2014", "1:2023");
    }

    @Test
    void ignoresInvalidRowsAndReturnsEmptyForMissingStatArray() {
        assertThat(mapper.toProviderLeagues(null)).isEmpty();
        assertThat(mapper.toProviderLeagues(new UnderStatDtos.StatDataResponse(null))).isEmpty();
        assertThat(mapper.toProviderLeagues(new UnderStatDtos.StatDataResponse(Arrays.asList(
                null,
                stat(null, "EPL", "2024", "8"),
                stat("1", null, "2024", "8")
        )))).isEmpty();
        assertThat(mapper.toProviderSeasons(new UnderStatDtos.StatDataResponse(null), "1")).isEmpty();
    }

    private static int currentSeasonStartYear() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return today.getMonthValue() >= 7 ? today.getYear() : today.getYear() - 1;
    }

    private static UnderStatDtos.LeagueStat stat(
            String id, String league, String year, String month) {
        return new UnderStatDtos.LeagueStat(id, league, "1.2", "1.1",
                "1.4", "1.2", year, month, "20");
    }
}
