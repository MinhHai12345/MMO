package com.mmo.module.fb.ingest.application.port.out;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.ProviderFixture;
import com.mmo.module.fb.ingest.service.ResolvedProviderFixture;

import java.util.List;

public interface FixturePersistenceService {
    boolean persistFixture(Long seasonId, Long homeTeamId, Long awayTeamId,
                           Provider provider, ProviderFixture fixture);

    /** Persists a bounded season batch; adapters should preload provider identities/candidates once. */
    default int persistFixtures(Long seasonId, Provider provider, List<ResolvedProviderFixture> fixtures) {
        int persisted = 0;
        if (fixtures == null) return 0;
        for (ResolvedProviderFixture fixture : fixtures) {
            if (fixture != null && persistFixture(seasonId, fixture.homeTeamId(), fixture.awayTeamId(),
                    provider, fixture.fixture())) persisted++;
        }
        return persisted;
    }
}
