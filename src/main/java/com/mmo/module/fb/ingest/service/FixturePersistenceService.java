package com.mmo.module.fb.ingest.service;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.ProviderFixture;

public interface FixturePersistenceService {
    boolean persistFixture(Long seasonId, Long homeTeamId, Long awayTeamId, Provider provider, ProviderFixture fixture);
}
