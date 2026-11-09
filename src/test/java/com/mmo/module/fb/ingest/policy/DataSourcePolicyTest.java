package com.mmo.module.fb.ingest.policy;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.league.entity.League;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DataSourcePolicyTest {

    private final DataSourcePolicy policy = new DataSourcePolicy();

    @Test
    void assignsHistoricalCoreToFootballDataUk() {
        for (int year = 2014; year <= 2022; year++) {
            assertEquals(Provider.FOOTBALL_DATA_UK,
                    policy.route(Dataset.FIXTURE, year).primary(), "season " + year);
            assertEquals(Provider.FOOTBALL_DATA_UK,
                    policy.route(Dataset.TEAM, year).primary(), "season " + year);
            assertNull(policy.route(Dataset.STANDINGS, year).primary());
        }
    }

    @Test
    void assignsModernCoreAndStandingsToFootballData() {
        for (int year = 2023; year <= 2035; year++) {
            assertEquals(Provider.FOOTBALL_DATA,
                    policy.route(Dataset.FIXTURE, year).primary(), "season " + year);
            assertEquals(Provider.FOOTBALL_DATA,
                    policy.route(Dataset.TEAM, year).primary(), "season " + year);
            assertEquals(Provider.FOOTBALL_DATA,
                    policy.route(Dataset.STANDINGS, year).primary(), "season " + year);
            assertTrue(policy.route(Dataset.FIXTURE, year).secondary()
                    .contains(Provider.FOOTBALL_DATA_UK));
        }
    }

    @Test
    void assignsStatisticsXgAndHistoricalOddsToUkForAllCoveredSeasons() {
        for (int year = 2014; year <= 2035; year++) {
            assertEquals(Provider.FOOTBALL_DATA_UK,
                    policy.route(Dataset.MATCH_STATISTICS, year).primary());
            assertEquals(Provider.FOOTBALL_DATA_UK,
                    policy.route(Dataset.MATCH_XG, year).primary());
            assertTrue(policy.route(Dataset.MATCH_STATISTICS, year).secondary()
                    .contains(Provider.UNDER_STATS));
            assertTrue(policy.route(Dataset.MATCH_XG, year).secondary()
                    .contains(Provider.UNDER_STATS));
            assertEquals(Provider.FOOTBALL_DATA_UK,
                    policy.route(Dataset.HISTORICAL_ODDS, year).primary());
        }
    }

    @Test
    void assignsSofaScoreAsOwnerForPlayerMatchStatistics() {
        for (int year = 2014; year <= 2035; year++) {
            assertEquals(Provider.SOFA_SCORE,
                    policy.route(Dataset.PLAYER_STATISTICS, year).primary(), "season " + year);
            assertTrue(policy.isAllowed(Provider.SOFA_SCORE, Dataset.PLAYER_STATISTICS,
                    Season.builder().year(year + "/" + (year + 1)).build()));
        }
    }

    @Test
    void scalarSeasonRoutingSupportsDetachedIngestionContexts() {
        assertTrue(policy.isPrimary(Provider.FOOTBALL_DATA_UK, Dataset.TEAM, "2022/2023"));
        assertFalse(policy.isPrimary(Provider.FOOTBALL_DATA_UK, Dataset.FIXTURE, "2023/2024"));
        assertTrue(policy.isAllowed(Provider.FOOTBALL_DATA_UK, Dataset.FIXTURE, "2023/2024"));
        assertTrue(policy.mayFallbackForCore(
                Provider.FOOTBALL_DATA_UK, Dataset.FIXTURE, "2023/2024", false));
        assertFalse(policy.mayFallbackForCore(
                Provider.FOOTBALL_DATA_UK, Dataset.FIXTURE, "2023/2024", true));
    }

    @Test
    void upcomingOddsRemainDisabled() {
        assertNull(policy.route(Dataset.UPCOMING_ODDS, 2026).primary());
    }

    @Test
    void parsesBothSeasonFormats() {
        assertEquals(2025, DataSourcePolicy.parseSeasonStartYear("2025/2026"));
        assertEquals(2025, DataSourcePolicy.parseSeasonStartYear("2025"));
        assertEquals(-1, DataSourcePolicy.parseSeasonStartYear("unknown"));
        Season season = Season.builder().year("2024/2025").build();
        assertTrue(policy.isPrimary(Provider.FOOTBALL_DATA, Dataset.FIXTURE, season));
    }

    @Test
    void resolvesSeasonSpecificPrimaryAndSecondarySourcesForSupportedLeague() {
        Season historical = Season.builder().year("2020/2021")
                .league(League.builder().name("LaLiga").build()).build();
        assertEquals(Provider.FOOTBALL_DATA_UK,
                policy.route(Dataset.FIXTURE, historical).primary());

        Season modern = Season.builder().year("2024/2025")
                .league(League.builder().name("La Liga").build()).build();
        assertEquals(Provider.FOOTBALL_DATA,
                policy.route(Dataset.FIXTURE, modern).primary());
        assertTrue(policy.route(Dataset.FIXTURE, modern).secondary()
                .contains(Provider.FOOTBALL_DATA_UK));
    }

    @Test
    void doesNotRouteUnsupportedCompetitionsToFootballDataUk() {
        Season season = Season.builder().year("2018/2019")
                .league(League.builder().name("UEFA Champions League").build()).build();
        assertNull(policy.route(Dataset.FIXTURE, season).primary());
        assertFalse(policy.isPrimary(Provider.FOOTBALL_DATA_UK, Dataset.MATCH_STATISTICS, season));
    }

    @Test
    void secondaryUkMayOnlyFallbackWhenFootballDataHasNoFixtureIdentity() {
        Season season = Season.builder().year("2025/2026")
                .league(League.builder().name("Premier League").build()).build();
        assertTrue(policy.mayFallbackForCore(Provider.FOOTBALL_DATA_UK,
                Dataset.FIXTURE, season, false));
        assertFalse(policy.mayFallbackForCore(Provider.FOOTBALL_DATA_UK,
                Dataset.FIXTURE, season, true));
    }
}
