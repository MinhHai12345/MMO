package com.mmo.module.fb.ingest.application.port.in;

/**
 * Inbound use-case contract for the Football-Data UK historical and active-season
 * import workflow. Schedulers and application orchestrators depend on this contract,
 * never on the implementation or its persistence/provider adapters.
 *
 * <p>The implementation is being decomposed into provider and persistence adapters
 * in subsequent refactoring steps. This interface intentionally preserves the
 * established workflow while its implementation is migrated.</p>
 */
public interface FootballDataUkIngestionUseCase {
    void prepareHistoricalSeasons();

    void syncCoreSeason(Long seasonId, boolean historical);

    void syncEnrichmentSeason(Long seasonId, boolean historical);

    boolean syncInitialData();

    void syncActiveSeasonsData();

    boolean syncInitialEnrichment();

    void syncActiveSeasonsEnrichment();
}
