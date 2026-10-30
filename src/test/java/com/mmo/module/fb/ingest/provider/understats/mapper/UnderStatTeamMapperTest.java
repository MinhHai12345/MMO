package com.mmo.module.fb.ingest.provider.understats.mapper;

import com.mmo.module.fb.ingest.provider.understats.dto.UnderStatDtos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UnderStatTeamMapperTest {
    private final UnderStatTeamMapper mapper = new UnderStatTeamMapper();

    @Test
    void mapsLeagueTeamsToProviderNeutralTeams() {
        var response = new UnderStatDtos.LeagueData(
                List.of(),
                Map.of("10", new UnderStatDtos.LeagueTeam("10", "Manchester City", List.of())),
                List.of());

        var teams = mapper.toProviderTeams(response);

        assertThat(teams).hasSize(1);
        assertThat(teams.get(0).externalId()).isEqualTo("10");
        assertThat(teams.get(0).name()).isEqualTo("Manchester City");
    }

    @Test
    void ignoresInvalidTeamRows() {
        var response = new UnderStatDtos.LeagueData(
                List.of(),
                Map.of("10", new UnderStatDtos.LeagueTeam("10", null, List.of())),
                List.of());

        assertThat(mapper.toProviderTeams(response)).isEmpty();
        assertThat(mapper.toProviderTeams(null)).isEmpty();
    }
}
