package com.mmo.module.fb.ingest.provider.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MmoProviderDataStandardizerTest {
    @Test
    void standardizesKnownLeagueAliasesAndWhitespace() {
        assertThat(MmoProviderDataStandardizer.leagueName(" Primera   Division ")).isEqualTo("LaLiga");
        assertThat(MmoProviderDataStandardizer.leagueName("la liga")).isEqualTo("LaLiga");
        assertThat(MmoProviderDataStandardizer.leagueName(" English Premier League ")).isEqualTo("Premier League");
        assertThat(MmoProviderDataStandardizer.teamName("  Real   Madrid CF ")).isEqualTo("Real Madrid CF");
        assertThat(MmoProviderDataStandardizer.teamShortName(" ", "Real Madrid CF")).isEqualTo("Real Madrid CF");
    }

    @Test
    void standardizesFixtureStatusesAndOddsVocabulary() {
        assertThat(MmoProviderDataStandardizer.fixtureStatus("finished")).isEqualTo("FT");
        assertThat(MmoProviderDataStandardizer.fixtureStatus("In Progress")).isEqualTo("LIVE");
        assertThat(MmoProviderDataStandardizer.fixtureStatus("postponed")).isEqualTo("PST");
        assertThat(MmoProviderDataStandardizer.oddsMarketName("Over/Under")).isEqualTo("Goals Over/Under");
        assertThat(MmoProviderDataStandardizer.oddsOutcomeName("1")).isEqualTo("Home");
        assertThat(MmoProviderDataStandardizer.oddsOutcomeName("Over 2,5")).isEqualTo("Over 2.5");
    }

    @Test
    void returnsNullForBlankDisplayValues() {
        assertThat(MmoProviderDataStandardizer.leagueName("  ")).isNull();
        assertThat(MmoProviderDataStandardizer.teamName(null)).isNull();
        assertThat(MmoProviderDataStandardizer.fixtureStatus(" ")).isNull();
    }
}
