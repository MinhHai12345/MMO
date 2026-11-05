package com.mmo.module.fb.ingest.application.port.out;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.enums.CheckpointStatus;
import com.mmo.module.fb.ingest.policy.Dataset;

/** Application-owned checkpoint contract; persistence details stay in the adapter layer. */
public interface ProviderCheckpointStore {
    boolean isCompleted(Provider provider, String checkpointKey);
    void markCompleted(Provider provider, String checkpointKey);

    default boolean isCompleted(Provider provider, Dataset dataset, Long leagueId, String season) {
        return isCompleted(provider, checkpointKey(dataset, leagueId, season));
    }

    void markStatus(Provider provider, Dataset dataset, Long leagueId,
                    String season, CheckpointStatus status);

    static String checkpointKey(Dataset dataset, Long leagueId, String season) {
        if (dataset == null || leagueId == null || season == null || season.isBlank()) {
            throw new IllegalArgumentException("Dataset, leagueId and season are required for a scoped checkpoint");
        }
        return dataset.name() + ":" + leagueId + ":" + season.trim();
    }
}
