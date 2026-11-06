package com.mmo.module.fb.ingest.application.port.in;

import com.mmo.module.fb.ingest.enums.Provider;

public interface StandingsIngestionUseCase {
    void syncStandings();
    void syncStandingsForProvider(Provider selectedProvider);
    void syncSeasonForProvider(Provider selectedProvider, Long seasonId);
}
