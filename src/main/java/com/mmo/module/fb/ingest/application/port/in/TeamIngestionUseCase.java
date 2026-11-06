package com.mmo.module.fb.ingest.application.port.in;

import com.mmo.module.fb.ingest.enums.Provider;

public interface TeamIngestionUseCase {
    void syncTeams();
    void syncTeamsForProvider(Provider selectedProvider);
    void syncAllSeasonsForProvider(Provider selectedProvider);
    void syncSeasonForProvider(Provider selectedProvider, Long seasonId);
}
