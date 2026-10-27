package com.mmo.module.fb.ingest.service;

import com.mmo.module.fb.ingest.enums.Provider;
import java.util.List;

/**
 * Coordinates the first real-data ingestion pass in dependency order.
 */
public interface FootballDataBootstrapService {
    void syncCoreData();

    void syncCoreData(ProviderOverrides overrides);

    /** Syncs core resources for multiple providers in dependency order. FOOTBALL_DATA remains canonical. */
    void syncCoreDataForProviders(List<Provider> providers);
}
