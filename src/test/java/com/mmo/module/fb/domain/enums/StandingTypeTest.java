package com.mmo.module.fb.domain.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StandingTypeTest {

    @Test
    void parsesProviderValuesCaseInsensitively() {
        assertEquals(StandingType.TOTAL, StandingType.fromProviderValue("total"));
        assertEquals(StandingType.HOME, StandingType.fromProviderValue(" Home "));
        assertEquals(StandingType.AWAY, StandingType.fromProviderValue("AWAY"));
    }

    @Test
    void defaultsMissingProviderScopeToTotal() {
        assertEquals(StandingType.TOTAL, StandingType.fromProviderValue(null));
        assertEquals(StandingType.TOTAL, StandingType.fromProviderValue("  "));
    }

    @Test
    void rejectsUnknownProviderScopeInsteadOfOverwritingTotalStanding() {
        assertThrows(IllegalArgumentException.class,
                () -> StandingType.fromProviderValue("ALL_TIME"));
    }
}
