package com.mmo.module.fb.ingest.service;

public interface StandingsIngestionService {
    void syncStandings();
    void syncStandingsForProvider(com.mmo.module.fb.domain.enums.Provider selectedProvider);
}
