package com.mmo.module.fb.ingest.provider.spi;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.Dataset;

import java.util.Set;

/**
 * Optional provider capability for importing a whole season in one batch (for example,
 * a season CSV that carries core and enrichment datasets together).
 */
public interface SeasonBatchProvider {
    Provider getProvider();
    Set<Dataset> supportedDatasets();
    void prepareHistoricalSeasons();
    void syncCoreSeason(Long seasonId, boolean historical);
    void syncEnrichmentSeason(Long seasonId, boolean historical);
}
