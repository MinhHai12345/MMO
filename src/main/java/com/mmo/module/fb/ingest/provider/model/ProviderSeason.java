package com.mmo.module.fb.ingest.provider.model;

import java.time.LocalDate;

/** Provider-specific season ID plus canonical season metadata. */
public record ProviderSeason(
        String externalId,
        String year,
        boolean current,
        LocalDate startDate,
        LocalDate endDate
) {
    /** Backward-compatible constructor for sources where the season ID equals its year. */
    public ProviderSeason(String year, boolean current, LocalDate startDate, LocalDate endDate) {
        this(year, year, current, startDate, endDate);
    }
}
