package com.mmo.module.fb.ingest.provider.understats.mapper;

import com.mmo.module.fb.ingest.provider.understats.dto.UnderStatDtos;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UnderStatFixtureMapperTest {
    private final UnderStatFixtureMapper mapper = new UnderStatFixtureMapper();

    @Test
    void mapsMatchRowsAndFiltersByDateRange() {
        var match = new UnderStatDtos.Match(
                "9001", true,
                new UnderStatDtos.TeamRef("10", "Manchester City", "MCI"),
                new UnderStatDtos.TeamRef("20", "Arsenal", "ARS"),
                new UnderStatDtos.Goals(2, 1),
                new UnderStatDtos.Xg(new java.math.BigDecimal("1.7"), new java.math.BigDecimal("0.8")),
                "2025-01-10 17:30:00",
                null);
        var response = new UnderStatDtos.LeagueData(List.of(match), null, null);

        var fixtures = mapper.toProviderFixtures(
                response, LocalDate.parse("2025-01-10"), LocalDate.parse("2025-01-10"));

        assertThat(fixtures).hasSize(1);
        assertThat(fixtures.get(0).externalId()).isEqualTo("9001");
        assertThat(fixtures.get(0).homeTeamExternalId()).isEqualTo("10");
        assertThat(fixtures.get(0).awayTeamExternalId()).isEqualTo("20");
        assertThat(fixtures.get(0).kickoff()).isEqualTo(Instant.parse("2025-01-10T17:30:00Z"));
        assertThat(fixtures.get(0).status()).isEqualTo("FINISHED");
        assertThat(fixtures.get(0).homeScore()).isEqualTo(2);
        assertThat(fixtures.get(0).awayScore()).isEqualTo(1);
    }

    @Test
    void returnsEmptyWhenNoMatchData() {
        assertThat(mapper.toProviderFixtures(null, null, null)).isEmpty();
        assertThat(mapper.toProviderFixtures(
                new UnderStatDtos.LeagueData(null, null, null), null, null)).isEmpty();
    }
}
