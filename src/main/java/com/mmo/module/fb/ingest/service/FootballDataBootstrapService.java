package com.mmo.module.fb.ingest.service;

/**
 * Coordinates the first real-data ingestion pass in dependency order.
 */
public interface FootballDataBootstrapService {
    void syncCoreData();

    void syncCoreData(ProviderOverrides overrides);
}
