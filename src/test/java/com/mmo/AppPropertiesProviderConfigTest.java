package com.mmo;

import com.mmo.module.fb.ingest.enums.Provider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AppPropertiesProviderConfigTest {

    @Test
    void providerConfigIsTheSingleSourceOfTruthForFootballData() {
        AppProperties properties = new AppProperties();
        AppProperties.ProviderConfig config = new AppProperties.ProviderConfig();
        config.setEnabled(true);
        config.setBaseUrl("https://api.football-data.org/v4");
        config.setApiKey("test-token");
        config.setLeagueIds(List.of(2021L));
        config.setSeasons(List.of("2023", "2024"));
        properties.getIngestion().getProviderConfig().put(Provider.FOOTBALL_DATA, config);

        AppProperties.FootballData clientConfig = properties.getFootballData();

        assertThat(clientConfig.isEnabled()).isTrue();
        assertThat(clientConfig.getBaseUrl()).isEqualTo("https://api.football-data.org/v4");
        assertThat(clientConfig.getApiKey()).isEqualTo("test-token");
        assertThat(properties.getIngestion().getProviderConfig().get(Provider.FOOTBALL_DATA).getLeagueIds())
                .containsExactly(2021L);
        assertThat(properties.getIngestion().getProviderConfig().get(Provider.FOOTBALL_DATA).getSeasons())
                .containsExactly("2023", "2024");
    }

    @Test
    void resolvesSofaScoreSpecificOptionsFromSharedProviderConfiguration() {
        AppProperties properties = new AppProperties();
        AppProperties.ProviderConfig config = new AppProperties.ProviderConfig();
        config.setEnabled(true);
        config.setBaseUrl("https://api.sofascore.com/api/v1/");
        config.setWebUrl("https://www.sofascore.com");
        config.setLeagueIds(List.of(17L));
        config.setOptions(Map.of("tournament-region", "EN"));
        properties.getIngestion().getProviderConfig().put(Provider.SOFA_SCORE, config);

        AppProperties.SofaScore clientConfig = properties.getSofaScore();

        assertThat(clientConfig.isEnabled()).isTrue();
        assertThat(clientConfig.getApi()).isEqualTo("https://api.sofascore.com/api/v1/");
        assertThat(clientConfig.getUrl()).isEqualTo("https://www.sofascore.com");
        assertThat(clientConfig.getTournamentRegion()).isEqualTo("EN");
    }

    @Test
    void providerConfigIsTheSingleSourceOfTruthForUnderstat() {
        AppProperties properties = new AppProperties();
        AppProperties.ProviderConfig config = new AppProperties.ProviderConfig();
        config.setEnabled(true);
        config.setBaseUrl("https://understat.com");
        config.setLeagueIds(List.of(2021L));
        config.setSeasons(List.of("2024"));
        properties.getIngestion().getProviderConfig().put(Provider.UNDER_STATS, config);

        AppProperties.Understats clientConfig = properties.getUnderstats();

        assertThat(clientConfig.isEnabled()).isTrue();
        assertThat(clientConfig.getBaseUrl()).isEqualTo("https://understat.com");
        assertThat(properties.getIngestion().getProviderConfig().get(Provider.UNDER_STATS).getLeagueIds())
                .containsExactly(2021L);
        assertThat(properties.getIngestion().getProviderConfig().get(Provider.UNDER_STATS).getSeasons())
                .containsExactly("2024");
    }

    @Test
    void resolvesApiFootballConnectionSettingsFromSharedProviderConfiguration() {
        AppProperties properties = new AppProperties();
        AppProperties.ProviderConfig config = new AppProperties.ProviderConfig();
        config.setEnabled(true);
        config.setBaseUrl("https://v3.football.api-sports.io");
        config.setApiKey("api-football-token");
        properties.getIngestion().getProviderConfig().put(Provider.API_FOOTBALL, config);

        AppProperties.ApiFootball clientConfig = properties.getApiFootball();

        assertThat(clientConfig.isEnabled()).isTrue();
        assertThat(clientConfig.getBaseUrl()).isEqualTo("https://v3.football.api-sports.io");
        assertThat(clientConfig.getApiKey()).isEqualTo("api-football-token");
    }
}
