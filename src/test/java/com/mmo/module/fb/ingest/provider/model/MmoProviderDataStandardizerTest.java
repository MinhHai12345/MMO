package com.mmo.module.fb.ingest.provider.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MmoProviderDataStandardizerTest {
    @Test
    void standardizesKnownLeagueAliasesToMmoDisplayNames() {
        assertThat(MmoProviderDataStandardizer.leagueName(" Primera   Division ")).isEqualTo("LaLiga");
        assertThat(MmoProviderDataStandardizer.leagueName("la liga")).isEqualTo("LaLiga");
        assertThat(MmoProviderDataStandardizer.leagueName("  English Premier League ")).isEqualTo("Premier League");
        assertThat(MmoProviderDataStandardizer.leagueName("  Eredivisie  ")).isEqualTo("Eredivisie");
    }

    @Test
    void normalizesDisplayTextWithoutChangingEntityIdentityOrSpelling() {
        assertThat(MmoProviderDataStandardizer.teamName("  Real   Madrid CF ")).isEqualTo("Real Madrid CF");
        assertThat(MmoProviderDataStandardizer.teamShortName(" ", " Real Madrid CF ")).isEqualTo("Real Madrid CF");
        assertThat(MmoProviderDataStandardizer.countryName("  England  ")).isEqualTo("England");
    }

    @Test
    void standardizesFixtureStatusesAcrossProviders() {
        assertThat(MmoProviderDataStandardizer.fixtureStatus("finished")).isEqualTo("FT");
        assertThat(MmoProviderDataStandardizer.fixtureStatus("In Progress")).isEqualTo("LIVE");
        assertThat(MmoProviderDataStandardizer.fixtureStatus("postponed")).isEqualTo("PST");
        assertThat(MmoProviderDataStandardizer.fixtureStatus("Scheduled")).isEqualTo("NS");
    }

    @Test
    void standardizesCommonOddsMarketsAndOutcomes() {
        assertThat(MmoProviderDataStandardizer.oddsMarketName("Match Winner")).isEqualTo("Match Winner");
        assertThat(MmoProviderDataStandardizer.oddsMarketName("Over/Under")).isEqualTo("Goals Over/Under");
        assertThat(MmoProviderDataStandardizer.oddsOutcomeName("1")).isEqualTo("Home");
        assertThat(MmoProviderDataStandardizer.oddsOutcomeName("Over 2,5")).isEqualTo("Over 2.5");
        assertThat(MmoProviderDataStandardizer.oddsOutcomeName("First goal")).isEqualTo("First goal");
    }

    @Test
    void returnsNullForBlankDisplayValues() {
        assertThat(MmoProviderDataStandardizer.leagueName("  ")).isNull();
        assertThat(MmoProviderDataStandardizer.teamName(null)).isNull();
        assertThat(MmoProviderDataStandardizer.fixtureStatus(" ")).isNull();
    }
}
