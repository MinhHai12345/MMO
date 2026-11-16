package com.mmo.module.fb.ingest.policy;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.domain.league.entity.League;
import com.mmo.module.fb.domain.league.entity.Season;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DataSourcePolicyTest {

    private final DataSourcePolicy policy = new DataSourcePolicy();

    @Test
    void shouldAllowSofaScoreAsFallbackForHistoricalUkSeasons() {
        ProviderRoute route = policy.route(Dataset.FIXTURE, 2018);

        assertEquals(Provider.FOOTBALL_DATA_UK, route.primary());
        assertTrue(route.secondary().contains(Provider.SOFA_SCORE));
        assertTrue(policy.mayFallbackForCore(Provider.SOFA_SCORE, Dataset.FIXTURE, "2018/2019", false));
        assertFalse(policy.mayFallbackForCore(Provider.SOFA_SCORE, Dataset.FIXTURE, "2018/2019", true));
    }

    @Test
    void shouldAllowSofaScoreAsFallbackForModernSeasons() {
        ProviderRoute route = policy.route(Dataset.TEAM, 2026);

        assertEquals(Provider.FOOTBALL_DATA, route.primary());
        assertTrue(route.secondary().contains(Provider.SOFA_SCORE));
        assertTrue(policy.isAllowed(Provider.SOFA_SCORE, Dataset.TEAM, "2026/2027"));
    }

    @Test
    void shouldAllowSofaScoreWhenNoOtherCoreOwnerExists() {
        ProviderRoute route = policy.route(Dataset.FIXTURE, 2010);

        assertNull(route.primary());
        assertTrue(route.secondary().contains(Provider.SOFA_SCORE));
        assertTrue(policy.mayFallbackForCore(Provider.SOFA_SCORE, Dataset.FIXTURE, "2010/2011", false));
    }


    @Test
    void shouldRetainSofaScoreFallbackForNonUkLeagues() {
        League league = mock(League.class);
        when(league.getName()).thenReturn("FIFA World Cup");
        Season season = mock(Season.class);
        when(season.getYear()).thenReturn("2026");
        when(season.getLeague()).thenReturn(league);

        ProviderRoute route = policy.route(Dataset.FIXTURE, season);

        assertEquals(Provider.FOOTBALL_DATA, route.primary());
        assertTrue(route.secondary().contains(Provider.SOFA_SCORE));
        assertFalse(route.secondary().contains(Provider.FOOTBALL_DATA_UK));
    }

    @Test
    void shouldKeepPrimaryProviderOwnershipUnchanged() {
        assertTrue(policy.isPrimary(Provider.FOOTBALL_DATA_UK, Dataset.FIXTURE, "2018/2019"));
        assertTrue(policy.isPrimary(Provider.FOOTBALL_DATA, Dataset.FIXTURE, "2026/2027"));
        assertFalse(policy.isPrimary(Provider.SOFA_SCORE, Dataset.FIXTURE, "2026/2027"));
    }
}
