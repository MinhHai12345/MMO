package com.mmo.module.fb.ingest.application.port.out;

import com.mmo.module.fb.ingest.application.model.provider.ProviderLineups;

/** Outbound persistence port for canonical player match statistics. */
public interface PlayerMatchStatisticsPersistencePort {
    int persist(Long matchId, ProviderLineups lineups);
}
