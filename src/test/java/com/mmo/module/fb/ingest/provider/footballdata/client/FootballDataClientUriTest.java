package com.mmo.module.fb.ingest.provider.footballdata.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FootballDataClientUriTest {

    @Test
    void expandsPathAndQueryPlaceholdersWithoutEncodingQueryDelimiter() {
        String uri = FootballDataClient.expandUri(
                "/competitions/{id}/teams?season={season}", 2002, 2026);

        assertThat(uri).isEqualTo("/competitions/2002/teams?season=2026");
        assertThat(uri).doesNotContain("%3F");
    }

    @Test
    void encodesQueryParameterValuesButKeepsQueryStructure() {
        String uri = FootballDataClient.expandUri(
                "/competitions/{id}/teams?season={season}", 2002, "2026 summer");

        assertThat(uri).isEqualTo("/competitions/2002/teams?season=2026%20summer");
    }

    @Test
    void supportsPathWithoutQueryParameters() {
        assertThat(FootballDataClient.expandUri("/competitions/{id}/teams", 2002))
                .isEqualTo("/competitions/2002/teams");
    }
}
