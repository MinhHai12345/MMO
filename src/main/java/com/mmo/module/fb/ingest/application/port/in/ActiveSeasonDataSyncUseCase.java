package com.mmo.module.fb.ingest.application.port.in;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.application.model.ProviderOverrides;

/**
 * Application use case for incremental football-data synchronization.
 *
 * <p>Implementations must fail when the workflow cannot acquire execution ownership;
 * callers must never interpret a skipped synchronization as a successful refresh.</p>
 */
public interface ActiveSeasonDataSyncUseCase {
    void sync(ProviderOverrides overrides, Provider oddsProviderOverride);
}
