package com.mmo.module.fb.ingest.application.port.out;

import com.mmo.module.fb.ingest.application.model.provider.ProviderStanding;
import com.mmo.module.fb.ingest.enums.Provider;

import java.util.List;

public interface StandingsPersistencePort {
    void upsertStanding(Provider provider, Long seasonId, Long teamId, ProviderStanding row);

    /** Persists one provider/season snapshot with bounded reads and one saveAll. */
    default int upsertStandings(Provider provider, Long seasonId, List<StandingRow> rows) {
        if (rows == null || rows.isEmpty()) return 0;
        int persisted = 0;
        for (StandingRow row : rows) {
            if (row == null || row.teamId() == null || row.standing() == null) continue;
            upsertStanding(provider, seasonId, row.teamId(), row.standing());
            persisted++;
        }
        return persisted;
    }

    record StandingRow(Long teamId, ProviderStanding standing) {}
}