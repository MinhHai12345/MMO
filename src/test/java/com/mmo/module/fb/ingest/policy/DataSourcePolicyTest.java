package com.mmo.module.fb.ingest.policy;

import com.mmo.module.fb.ingest.enums.Provider;
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
    void shouldKeepPrimaryProviderOwnershipUnchanged() {
        assertTrue(policy.isPrimary(Provider.FOOTBALL_DATA_UK, Dataset.FIXTURE, "2018/2019"));
        assertTrue(policy.isPrimary(Provider.FOOTBALL_DATA, Dataset.FIXTURE, "2026/2027"));
        assertFalse(policy.isPrimary(Provider.SOFA_SCORE, Dataset.FIXTURE, "2026/2027"));
    }
}
