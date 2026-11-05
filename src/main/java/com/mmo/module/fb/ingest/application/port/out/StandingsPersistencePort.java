package com.mmo.module.fb.ingest.application.port.out;

import com.mmo.module.fb.ingest.provider.model.ProviderStanding;
import com.mmo.module.fb.ingest.enums.Provider;

public interface StandingsPersistencePort {
    void upsertStanding(Provider provider, Long seasonId, Long teamId, ProviderStanding row);
}
