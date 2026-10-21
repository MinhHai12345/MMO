package com.mmo.module.fb.domain.enums;

/** Scope of a league table row. */
public enum StandingType {
    TOTAL,
    HOME,
    AWAY;

    public static StandingType fromProviderValue(String value) {
        if (value == null || value.isBlank()) return TOTAL;
        try {
            return StandingType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return TOTAL;
        }
    }
}
