package com.mmo.module.fb.match.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MatchProviderTest {

    @Test
    void mapsConfiguredProviderNamesAtTheModuleBoundary() {
        assertEquals(MatchProvider.SOFA_SCORE, MatchProvider.fromName("SOFA_SCORE"));
        assertEquals(MatchProvider.FOOTBALL_DATA_UK, MatchProvider.fromName("football_data_uk"));
    }

    @Test
    void rejectsMissingOrUnknownProviderNames() {
        assertThrows(IllegalArgumentException.class, () -> MatchProvider.fromName(null));
        assertThrows(IllegalArgumentException.class, () -> MatchProvider.fromName("UNKNOWN_PROVIDER"));
    }
}
