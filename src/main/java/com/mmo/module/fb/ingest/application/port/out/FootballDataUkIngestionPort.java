package com.mmo.module.fb.ingest.application.port.out;

/** Provider-specific Core and enrichment workflow exposed to the application layer. */
public interface FootballDataUkIngestionPort {
    void prepareHistoricalSeasons();
    void syncCoreSeason(Long seasonId, boolean historical);
    void syncEnrichmentSeason(Long seasonId, boolean historical);
    boolean syncInitialData();
    void syncActiveSeasonsData();

    /** @deprecated use {@link #syncActiveSeasonsData()} */
    @Deprecated
    default void syncCurrentSeasonData() {
        syncActiveSeasonsData();
    }
    boolean syncInitialEnrichment();
    void syncActiveSeasonsEnrichment();

    /** @deprecated use {@link #syncActiveSeasonsEnrichment()} */
    @Deprecated
    default void syncCurrentSeasonEnrichment() {
        syncActiveSeasonsEnrichment();
    }
}
