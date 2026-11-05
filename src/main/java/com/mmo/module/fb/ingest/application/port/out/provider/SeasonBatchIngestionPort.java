package com.mmo.module.fb.ingest.application.port.out.provider;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.Dataset;

import java.util.Set;

/**
 * Application-owned provider capability for sources that import a season as a batch.
 * The contract is deliberately independent from provider implementation packages.
 */
public interface SeasonBatchIngestionPort {
    Provider getProvider();
    Set<Dataset> supportedDatasets();
    void prepareHistoricalSeasons();
    void syncCoreSeason(Long seasonId, boolean historical);
    void syncEnrichmentSeason(Long seasonId, boolean historical);
}
