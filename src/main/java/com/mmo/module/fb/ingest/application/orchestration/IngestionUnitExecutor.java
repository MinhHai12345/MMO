package com.mmo.module.fb.ingest.application.orchestration;

import com.mmo.module.fb.ingest.application.exception.IngestionPartialException;
import com.mmo.module.fb.ingest.application.port.out.ProviderCheckpointStore;
import com.mmo.module.fb.ingest.enums.CheckpointStatus;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Executes and checkpoints one work unit. Historical runs may skip terminal outcomes;
 * active runs always execute so checkpoints never suppress scheduled refreshes.
 */
public final class IngestionUnitExecutor {

    private final ProviderCheckpointStore checkpointStore;

    public IngestionUnitExecutor(ProviderCheckpointStore checkpointStore) {
        this.checkpointStore = Objects.requireNonNull(checkpointStore, "checkpointStore must not be null");
    }

    public IngestionUnitResult execute(IngestionWorkUnit unit, boolean historical, Runnable operation) {
        Objects.requireNonNull(operation, "operation must not be null");
        return executeWithResult(unit, historical, () -> {
            operation.run();
            return IngestionUnitResult.completed(unit, 0);
        });
    }

    public IngestionUnitResult executeWithResult(
            IngestionWorkUnit unit, boolean historical, Supplier<IngestionUnitResult> operation) {
        Objects.requireNonNull(unit, "unit must not be null");
        Objects.requireNonNull(operation, "operation must not be null");

        if (historical && checkpointStore.isSatisfied(
                unit.provider(), unit.dataset(), unit.leagueId(), unit.season())) {
            return IngestionUnitResult.skipped(unit);
        }

        long startedAt = System.nanoTime();
        checkpointStore.markStatus(unit.provider(), unit.dataset(), unit.leagueId(),
                unit.season(), CheckpointStatus.RUNNING);
        try {
            IngestionUnitResult result = Objects.requireNonNull(operation.get(),
                    "Work-unit operation returned null");
            if (!unit.equals(result.unit())) {
                throw new IllegalStateException("Work-unit operation returned a result for a different unit");
            }
            if (result.status() == CheckpointStatus.PENDING || result.status() == CheckpointStatus.RUNNING) {
                throw new IllegalStateException("Work-unit operation returned a non-terminal status: "
                        + result.status());
            }
            checkpointStore.markStatus(unit.provider(), unit.dataset(), unit.leagueId(),
                    unit.season(), result.status());
            return withDuration(result, elapsedMillis(startedAt));
        } catch (IngestionPartialException exception) {
            checkpointStore.markStatus(unit.provider(), unit.dataset(), unit.leagueId(),
                    unit.season(), CheckpointStatus.PARTIAL);
            return IngestionUnitResult.partial(unit, exception.getMessage(), elapsedMillis(startedAt));
        } catch (RuntimeException exception) {
            checkpointStore.markStatus(unit.provider(), unit.dataset(), unit.leagueId(),
                    unit.season(), CheckpointStatus.FAILED);
            return IngestionUnitResult.failed(unit, exception.getMessage(), elapsedMillis(startedAt));
        }
    }

    private static IngestionUnitResult withDuration(IngestionUnitResult result, long durationMillis) {
        return new IngestionUnitResult(result.unit(), result.status(), result.fetched(), result.persisted(),
                result.skippedRows(), result.failedRows(), result.skipped(), durationMillis, result.message());
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
