package com.mmo.module.fb.ingest.service;

import com.mmo.module.fb.ingest.enums.Provider;

/** Application use case for the incremental football-data synchronization workflow. */
public interface FootballDataSyncService {
    void initializeProviderMappings();
    void sync(ProviderOverrides overrides, Provider oddsProviderOverride);
}
