package com.mmo.module.fb.ingest.service;

import com.mmo.module.fb.ingest.enums.Provider;

public interface LeagueIngestionService {
    void syncLeagues();
    void syncLeaguesForProvider(Provider selectedProvider);
}
