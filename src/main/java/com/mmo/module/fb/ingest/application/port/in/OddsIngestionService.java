package com.mmo.module.fb.ingest.application.port.in;

import com.mmo.module.fb.ingest.enums.Provider;

public interface OddsIngestionService {
    void syncUpcomingOdds();

    void syncUpcomingOdds(Provider provider);
}
