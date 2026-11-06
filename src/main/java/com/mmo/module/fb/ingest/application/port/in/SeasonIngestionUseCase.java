package com.mmo.module.fb.ingest.application.port.in;

import com.mmo.module.fb.ingest.enums.Provider;

public interface SeasonIngestionUseCase {
    void syncSeasonsForProvider(Provider selectedProvider);
}
