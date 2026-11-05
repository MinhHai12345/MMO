package com.mmo.module.fb.ingest.application.port.out;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.ProviderOdds;

import java.util.List;

public interface OddsPersistenceService {

    /** Backward-compatible entry point for older callers. */
    void persistOdds(Long matchId, List<ProviderOdds> values);

    /** Provider-aware persistence entry point used by the ingestion workflow. */
    default void persistOdds(Long matchId, Provider provider, List<ProviderOdds> values) {
        persistOdds(matchId, values);
    }
}
