package com.mmo.module.fb.ingest.application.port.in;

import com.mmo.module.fb.ingest.enums.Provider;

public interface LeagueIngestionUseCase {
    void syncLeagues();
    void syncLeaguesForProvider(Provider selectedProvider);
}
