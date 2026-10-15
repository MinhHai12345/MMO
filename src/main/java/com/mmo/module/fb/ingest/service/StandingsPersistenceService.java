package com.mmo.module.fb.ingest.service;

public interface StandingsPersistenceService {
    void upsertStanding(Long seasonId, Long teamId, com.mmo.module.fb.ingest.provider.model.ProviderStanding row);
}
