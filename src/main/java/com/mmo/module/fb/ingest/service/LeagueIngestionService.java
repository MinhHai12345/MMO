package com.mmo.module.fb.ingest.service;

public interface LeagueIngestionService {
    void syncLeagues();
    void syncLeaguesForProvider(com.mmo.module.fb.domain.enums.Provider selectedProvider);
}
