package com.mmo.module.fb.ingest.application.port.in;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.service.ProviderOverrides;

/** Application use case for the incremental football-data synchronization workflow. */
public interface FootballDataSyncService {
    void sync(ProviderOverrides overrides, Provider oddsProviderOverride);
}
