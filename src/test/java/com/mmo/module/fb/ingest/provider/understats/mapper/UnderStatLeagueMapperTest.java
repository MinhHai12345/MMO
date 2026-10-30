package com.mmo.module.fb.ingest.provider.understats.mapper;

import com.mmo.module.fb.ingest.provider.model.ProviderLeague;
import com.mmo.module.fb.ingest.provider.understats.dto.UnderStatDtos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UnderStatLeagueMapperTest {
    private final UnderStatLeagueMapper mapper = new UnderStatLeagueMapper();

    @Test
    void groupsMonthlyStatRowsIntoProviderLeaguesAndDistinctSeasons() {
        UnderStatDtos.StatDataResponse response = new UnderStatDtos.StatDataResponse(List.of(
                stat("1", "EPL", "2024", "8"),
                stat("1", "EPL", "2024", "9"),
                stat("1", "EPL", "2025", "8"),
                stat("2", "La Liga", "2025", "8"),
                stat("2", "La Liga", "2025", "9"),
                stat("3", "Bundesliga", "2025", "8")
        ));

        List<ProviderLeague> leagues = mapper.toProviderLeagues(response);

        assertThat(leagues).hasSize(3);
        ProviderLeague epl = leagues.get(0);
        assertThat(epl.externalId()).isEqualTo("1");
        assertThat(epl.identify()).isEqualTo("EPL");
        assertThat(epl.name()).isEqualTo("Premier League");
        assertThat(epl.country()).isEqualTo("England");
        assertThat(epl.seasons()).extracting("externalId")
                .containsExactly("1:2024", "1:2025");
        assertThat(epl.seasons()).extracting("year").containsExactly("2024", "2025");

        ProviderLeague laLiga = leagues.get(1);
        assertThat(laLiga.identify()).isEqualTo("La_Liga");
        assertThat(laLiga.name()).isEqualTo("LaLiga");
        assertThat(laLiga.seasons()).hasSize(1);
    }

    @Test
    void ignoresInvalidRowsAndReturnsEmptyForMissingStatArray() {
        assertThat(mapper.toProviderLeagues(null)).isEmpty();
        assertThat(mapper.toProviderLeagues(new UnderStatDtos.StatDataResponse(null))).isEmpty();
        assertThat(mapper.toProviderLeagues(new UnderStatDtos.StatDataResponse(List.of(
                null,
                stat(null, "EPL", "2024", "8"),
                stat("1", null, "2024", "8"),
                stat("1", "EPL", "unknown", "8")
        )))).isEmpty();
    }

    private static UnderStatDtos.LeagueStat stat(
            String id, String league, String year, String month) {
        return new UnderStatDtos.LeagueStat(id, league, "1.2", "1.1",
                "1.4", "1.2", year, month, "20");
    }
}
