package com.mmo;

import com.mmo.module.fb.ingest.enums.Provider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AppPropertiesProviderConfigTest {

    @Test
    void resolvesFootballDataFromSharedProviderConfiguration() {
        AppProperties properties = new AppProperties();
        AppProperties.ProviderConfig config = new AppProperties.ProviderConfig();
        config.setEnabled(true);
        config.setBaseUrl("https://api.football-data.org/v4");
        config.setApiKey("test-token");
        config.setLeagueIds(List.of(PL_ID));
        config.setSeasons(List.of("2023", "2024"));
        properties.getIngestion().getProviderConfig().put(Provider.FOOTBALL_DATA, config);

        AppProperties.FootballData resolved = properties.getFootballData();

        assertThat(resolved.isEnabled()).isTrue();
        assertThat(resolved.getBaseUrl()).isEqualTo("https://api.football-data.org/v4");
        assertThat(resolved.getApiKey()).isEqualTo("test-token");
        assertThat(resolved.getCompetitionIds()).containsExactly(PL_ID);
        assertThat(resolved.getSeasons()).containsExactly("2023", "2024");
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

        AppProperties.SofaScore resolved = properties.getSofaScore();

        assertThat(resolved.getApi()).isEqualTo("https://api.sofascore.com/api/v1/");
        assertThat(resolved.getUrl()).isEqualTo("https://www.sofascore.com");
        assertThat(resolved.getTournamentRegion()).isEqualTo("EN");
    }

    @Test
    void resolvesUnderstatConfigurationIncludingEnabledAndSeasonFilters() {
        AppProperties properties = new AppProperties();
        AppProperties.ProviderConfig config = new AppProperties.ProviderConfig();
        config.setEnabled(true);
        config.setBaseUrl("https://understat.com");
        config.setLeagueIds(List.of(PL_ID));
        config.setSeasons(List.of("2024"));
        properties.getIngestion().getProviderConfig().put(Provider.UNDER_STATS, config);

        AppProperties.Understats resolved = properties.getUnderstats();

        assertThat(resolved.isEnabled()).isTrue();
        assertThat(resolved.getBaseUrl()).isEqualTo("https://understat.com");
        assertThat(resolved.getLeagueIds()).containsExactly(PL_ID);
        assertThat(resolved.getSeasons()).containsExactly("2024");
    }

    @Test
    void resolvesApiFootballSeasonFiltersFromSharedProviderConfiguration() {
        AppProperties properties = new AppProperties();
        AppProperties.ProviderConfig config = new AppProperties.ProviderConfig();
        config.setEnabled(true);
        config.setSeasons(List.of("2023", "2024"));
        properties.getIngestion().getProviderConfig().put(Provider.API_FOOTBALL, config);

        assertThat(properties.getApiFootball().getSeasons()).containsExactly("2023", "2024");
    }

    @Test
    void fallsBackToLegacyTypedConfigurationWhenSharedEntryIsAbsent() {
        AppProperties properties = new AppProperties();
        properties.getFootballData().setBaseUrl("https://legacy.example");
        properties.getFootballData().setSeasons(List.of("2025"));

        assertThat(properties.getFootballData().getBaseUrl()).isEqualTo("https://legacy.example");
        assertThat(properties.getFootballData().getSeasons()).containsExactly("2025");
    }

    private static final long PL_ID = 2021L;
}
