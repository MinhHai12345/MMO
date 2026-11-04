package com.mmo.module.fb.ingest.provider.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MmoProviderDataStandardizerTest {
    @Test
    void standardizesKnownLeagueAliasesAndWhitespace() {
        assertThat(MmoProviderDataStandardizer.leagueName(" Primera   Division ")).isEqualTo("LaLiga");
        assertThat(MmoProviderDataStandardizer.leagueName("la liga")).isEqualTo("LaLiga");
        assertThat(MmoProviderDataStandardizer.leagueName(" English Premier League ")).isEqualTo("Premier League");
        assertThat(MmoProviderDataStandardizer.teamName("  Real   Madrid CF ")).isEqualTo("Real Madrid");
        assertThat(MmoProviderDataStandardizer.teamShortName(" ", "Real Madrid CF")).isEqualTo("Real Madrid");
    }

    @Test
    void standardizesTeamNamesForCanonicalStorage() {
        assertThat(MmoProviderDataStandardizer.standardTeamName("  FC   Bayern Mu\u0308nchen  "))
                .isEqualTo("Bayern Munich");
        assertThat(MmoProviderDataStandardizer.standardTeamName("Paris Saint–Germain"))
                .isEqualTo("Paris Saint-Germain");
        assertThat(MmoProviderDataStandardizer.standardTeamName("O’Connell FC"))
                .isEqualTo("O'Connell FC");
        assertThat(MmoProviderDataStandardizer.standardTeamName("  ")).isNull();
    }

    @Test
    void mapsProviderTeamAliasesToPopularCanonicalNames() {
        assertThat(MmoProviderDataStandardizer.standardTeamName("FC Bayern München"))
                .isEqualTo("Bayern Munich");
        assertThat(MmoProviderDataStandardizer.standardTeamName("Bayern Munich"))
                .isEqualTo("Bayern Munich");
        assertThat(MmoProviderDataStandardizer.standardTeamName("1. FC Köln"))
                .isEqualTo("FC Köln");
        assertThat(MmoProviderDataStandardizer.standardTeamName("FC Cologne"))
                .isEqualTo("FC Köln");
        assertThat(MmoProviderDataStandardizer.standardTeamName("Borussia M.Gladbach"))
                .isEqualTo("Borussia Mönchengladbach");
        assertThat(MmoProviderDataStandardizer.standardTeamName("FC Internazionale Milano"))
                .isEqualTo("Inter");
        assertThat(MmoProviderDataStandardizer.standardTeamName("Atalanta BC"))
                .isEqualTo("Atalanta");
        assertThat(MmoProviderDataStandardizer.standardTeamName("Brighton & Hove Albion FC"))
                .isEqualTo("Brighton");
        assertThat(MmoProviderDataStandardizer.standardTeamName("RCD Espanyol de Barcelona"))
                .isEqualTo("Espanyol");
        assertThat(MmoProviderDataStandardizer.standardTeamName("Unknown FC"))
                .isEqualTo("Unknown FC");
    }

    @Test
    void standardizesFootballDataUkTeamAliases() {
        assertThat(MmoProviderDataStandardizer.standardTeamName("Man City"))
                .isEqualTo("Manchester City");
        assertThat(MmoProviderDataStandardizer.standardTeamName("Man United"))
                .isEqualTo("Manchester United");
        assertThat(MmoProviderDataStandardizer.standardTeamName("Man Utd"))
                .isEqualTo("Manchester United");
        assertThat(MmoProviderDataStandardizer.standardTeamName("Nott'm Forest"))
                .isEqualTo("Nottingham Forest");
        assertThat(MmoProviderDataStandardizer.standardTeamName("Newcastle"))
                .isEqualTo("Newcastle United");
        assertThat(MmoProviderDataStandardizer.standardTeamName("Spurs"))
                .isEqualTo("Tottenham");
        assertThat(MmoProviderDataStandardizer.standardTeamName("West Brom"))
                .isEqualTo("West Bromwich Albion");
        assertThat(MmoProviderDataStandardizer.standardTeamName("Sheffield Weds"))
                .isEqualTo("Sheffield Wednesday");
        assertThat(MmoProviderDataStandardizer.standardTeamName("QPR"))
                .isEqualTo("Queens Park Rangers");
    }

    @Test
    void createsConservativeCrossProviderTeamNameKeys() {
        assertThat(MmoProviderDataStandardizer.teamNameMatchKey("FC Bayern München"))
                .isEqualTo("bayern munich");
        assertThat(MmoProviderDataStandardizer.teamNameMatchKey("Bayern Munchen"))
                .isEqualTo("bayern munchen");
        assertThat(MmoProviderDataStandardizer.teamNameMatchKey("Paris Saint-Germain"))
                .isEqualTo("paris saint germain");
        assertThat(MmoProviderDataStandardizer.teamNameMatchKey("Paris Saint Germain"))
                .isEqualTo("paris saint germain");
        assertThat(MmoProviderDataStandardizer.teamNameMatchKey("Manchester United"))
                .isNotEqualTo(MmoProviderDataStandardizer.teamNameMatchKey("Manchester City"));
        assertThat(MmoProviderDataStandardizer.teamNameMatchKey("  ")).isNull();
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
