package com.mmo.module.fb.ingest.application.port.in;

import com.mmo.module.fb.ingest.enums.Provider;

public interface SeasonIngestionService {
    void syncSeasonsForProvider(Provider selectedProvider);
}
