package com.mmo;

import com.mmo.module.fb.ingest.enums.Provider;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AppPropertiesProviderConfigTest {

    @Test
    void providerConfigIsTheSingleSourceOfTruthForConnectionSettings() {
        AppProperties properties = new AppProperties();
        AppProperties.ProviderConfig config = new AppProperties.ProviderConfig();
        config.setEnabled(true);
        config.setUri("https://api.football-data.org/v4");
        config.setApiKey("test-token");
        config.setOptions(Map.of("catalogue-endpoint", "/competitions"));
        properties.getIngestion().getProviderConfig().put(Provider.FOOTBALL_DATA, config);

        AppProperties.ProviderConfig actual =
                properties.getIngestion().getProviderConfig().get(Provider.FOOTBALL_DATA);
        assertThat(actual.isEnabled()).isTrue();
        assertThat(actual.getUri()).isEqualTo("https://api.football-data.org/v4");
        assertThat(actual.getApiKey()).isEqualTo("test-token");
        assertThat(actual.getOptions()).containsEntry("catalogue-endpoint", "/competitions");
    }

    @Test
    void providerConfigDoesNotContainLeagueOrSeasonAllowLists() {
        assertThat(AppProperties.ProviderConfig.class.getDeclaredFields())
                .extracting("name")
                .containsExactlyInAnyOrder("enabled", "baseUrl", "webUrl", "apiKey", "options");
    }
}
