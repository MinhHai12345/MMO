package com.mmo.module.fb.ingest.service;

public interface TeamIngestionService {
    void syncTeams();
    void syncTeamsForProvider(com.mmo.module.fb.domain.enums.Provider selectedProvider);
}
