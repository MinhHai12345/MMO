package com.mmo.module.fb.ingest.application.port.out;

import com.mmo.module.fb.ingest.application.model.IngestionCheckpointProgress;
import com.mmo.module.fb.ingest.enums.CheckpointStatus;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.Dataset;

import java.util.Optional;

/** Application-owned checkpoint contract; persistence details stay in the adapter layer. */
public interface ProviderCheckpointPort {
    boolean isCompleted(Provider provider, String checkpointKey);
    void markCompleted(Provider provider, String checkpointKey);

    default boolean isCompleted(Provider provider, Dataset dataset, Long leagueId, String season) {
        return isCompleted(provider, checkpointKey(dataset, leagueId, season));
    }

    /**
     * True when a historical unit reached a terminal outcome. NOT_AVAILABLE is terminal
     * for a historical backfill, but is deliberately not treated as successfully completed.
     * Incremental workflows should still refresh active seasons regardless of this value.
     */
    default boolean isSatisfied(Provider provider, Dataset dataset, Long leagueId, String season) {
        return isCompleted(provider, dataset, leagueId, season);
    }

    void markStatus(Provider provider, Dataset dataset, Long leagueId,
                    String season, CheckpointStatus status);

    /** Returns the last committed batch cursor for a provider/dataset/league/season unit. */
    default Optional<IngestionCheckpointProgress> findProgress(
            Provider provider, Dataset dataset, Long leagueId, String season) {
        return Optional.empty();
    }

    /** Persists progress only after the corresponding batch has been durably written. */
    default void saveProgress(Provider provider, Dataset dataset, Long leagueId, String season,
                              String cursor, long processedRows, long persistedRows, long failedRows) {
        throw new UnsupportedOperationException("Checkpoint progress is not supported by this adapter");
    }

    /** Clears a completed or invalidated cursor while preserving the terminal checkpoint state. */
    default void clearProgress(Provider provider, Dataset dataset, Long leagueId, String season) {
        // Optional for adapters that do not persist resumable progress.
    }

    static String checkpointKey(Dataset dataset, Long leagueId, String season) {
        if (dataset == null || leagueId == null || season == null || season.isBlank()) {
            throw new IllegalArgumentException("Dataset, leagueId and season are required for a scoped checkpoint");
        }
        return dataset.name() + ":" + leagueId + ":" + season.trim();
    }
}
