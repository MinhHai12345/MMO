package com.mmo.module.fb.ingest.service;

import com.mmo.module.fb.ingest.provider.model.ProviderStanding;

public interface StandingsPersistenceService {
    void upsertStanding(Long seasonId, Long teamId, ProviderStanding row);
}
