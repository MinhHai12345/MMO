package com.mmo.module.fb.ingest.application.port.in;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.application.model.ProviderOverrides;

/** Application use case for the incremental football-data synchronization workflow. */
public interface ActiveSeasonDataSyncUseCase {
    void sync(ProviderOverrides overrides, Provider oddsProviderOverride);
}
