package com.mmo.module.fb.domain.enums;

/** Scope of a league table row supplied by a football data provider. */
public enum StandingType {
    TOTAL,
    HOME,
    AWAY;

    /**
     * Converts a provider's standing scope into the canonical domain value.
     *
     * <p>Null or blank values default to {@link #TOTAL} because some providers
     * omit the scope for their overall table. Unknown non-blank values are
     * rejected instead of being silently treated as TOTAL, which could cause
     * home/away rows to overwrite the overall table.</p>
     *
     * @param value provider-supplied scope
     * @return canonical standing type
     * @throws IllegalArgumentException when a non-blank value is unsupported
     */
    public static StandingType fromProviderValue(String value) {
        if (value == null || value.isBlank()) {
            return TOTAL;
        }

        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        try {
            return StandingType.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unsupported standing type from provider: '" + value + "'", exception);
        }
    }
}
