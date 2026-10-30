package com.mmo.module.fb.ingest.service;

import com.mmo.module.fb.ingest.enums.Provider;

public interface SeasonIngestionService {
    void syncSeasonsForProvider(Provider selectedProvider);
}
