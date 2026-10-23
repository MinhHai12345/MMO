package com.mmo.module.fb.ingest.service;

import com.mmo.module.fb.ingest.enums.Provider;

public interface StandingsIngestionService {
    void syncStandings();
    void syncStandingsForProvider(Provider selectedProvider);
}
