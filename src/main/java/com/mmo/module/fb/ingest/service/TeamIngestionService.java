package com.mmo.module.fb.ingest.service;

import com.mmo.module.fb.ingest.enums.Provider;

public interface TeamIngestionService {
    void syncTeams();
    void syncTeamsForProvider(Provider selectedProvider);
}
