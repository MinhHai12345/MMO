package com.mmo.module.fb.ingest.service;

public interface FixturePersistenceService {
    boolean persistFixture(Long seasonId, Long homeTeamId, Long awayTeamId, com.mmo.module.fb.domain.enums.Provider provider, com.mmo.module.fb.ingest.provider.model.ProviderFixture fixture);
}
