package com.mmo.module.fb.ingest.provider.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MmoProviderDataStandardizerTest {

    @Test
    void standardizesKnownLeagueAliasesToMmoDisplayNames() {
        assertThat(MmoProviderDataStandardizer.leagueName(" Primera   Division "))
                .isEqualTo("LaLiga");
        assertThat(MmoProviderDataStandardizer.leagueName("la liga"))
                .isEqualTo("LaLiga");
        assertThat(MmoProviderDataStandardizer.leagueName("  English Premier League "))
                .isEqualTo("Premier League");
    }

    @Test
    void normalizesTeamWhitespaceWithoutChangingTeamIdentityOrSpelling() {
        assertThat(MmoProviderDataStandardizer.teamName("  Real   Madrid CF "))
                .isEqualTo("Real Madrid CF");
        assertThat(MmoProviderDataStandardizer.teamShortName(" ", " Real Madrid CF "))
                .isEqualTo("Real Madrid CF");
    }

    @Test
    void leavesUnknownLeagueNamesReadableAndReturnsNullForBlankValues() {
        assertThat(MmoProviderDataStandardizer.leagueName("  Eredivisie  "))
                .isEqualTo("Eredivisie");
        assertThat(MmoProviderDataStandardizer.leagueName("  ")).isNull();
        assertThat(MmoProviderDataStandardizer.teamName(null)).isNull();
    }
}
