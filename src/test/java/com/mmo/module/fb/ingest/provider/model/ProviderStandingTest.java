package com.mmo.module.fb.ingest.provider.model;

import com.mmo.module.fb.domain.league.enums.StandingType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProviderStandingTest {

    @Test
    void acceptsConsistentCompleteTableRow() {
        ProviderStanding row = new ProviderStanding("57", 1, 25, 14, "WWDWW", null,
                10, 8, 1, 1, 22, 8, StandingType.TOTAL);

        assertTrue(row.consistencyIssues().isEmpty());
    }

    @Test
    void reportsPlayedAndGoalDifferenceMismatches() {
        ProviderStanding row = new ProviderStanding("57", 1, 25, 13, "WWDWW", null,
                10, 8, 1, 0, 22, 8, StandingType.TOTAL);

        assertEquals(2, row.consistencyIssues().size());
    }

    @Test
    void doesNotInventMissingStatistics() {
        ProviderStanding row = new ProviderStanding("57", 1, 25, null, null, null,
                null, null, null, null, null, null, StandingType.TOTAL);

        assertTrue(row.consistencyIssues().isEmpty());
        assertNull(row.played());
        assertNull(row.goalDifference());
    }
}
