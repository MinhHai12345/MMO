package com.mmo.module.fb.ingest.application.orchestration;

import com.mmo.module.fb.ingest.application.exception.IngestionPartialException;
import com.mmo.module.fb.ingest.application.exception.ProviderDataException;
import com.mmo.module.fb.ingest.application.exception.ProviderTransportException;
import com.mmo.module.fb.ingest.application.lock.IngestionDistributedLock;
import com.mmo.module.fb.ingest.application.port.out.ProviderCheckpointPort;
import com.mmo.module.fb.ingest.enums.CheckpointStatus;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.TransactionException;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Executes one provider/dataset/league/season work unit under a distributed lock
 * and persists its checkpoint transition. Historical runs may skip terminal outcomes;
 * active runs always execute so checkpoints never suppress scheduled refreshes.
 */
public final class IngestionUnitExecutor {

    private final ProviderCheckpointPort checkpointStore;
    private final IngestionDistributedLock distributedLock;

    public IngestionUnitExecutor(ProviderCheckpointPort checkpointStore) {
        this(checkpointStore, null);
    }

    public IngestionUnitExecutor(ProviderCheckpointPort checkpointStore,
                                 IngestionDistributedLock distributedLock) {
        this.checkpointStore = Objects.requireNonNull(checkpointStore, "checkpointStore must not be null");
        this.distributedLock = distributedLock;
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

        if (distributedLock == null) {
            return executeUnlocked(unit, historical, operation);
        }

        AtomicReference<IngestionUnitResult> result = new AtomicReference<>();
        boolean acquired = distributedLock.executeScoped(lockScope(unit),
                () -> result.set(executeUnlocked(unit, historical, operation)));
        if (!acquired) {
            return IngestionUnitResult.failed(unit,
                    "Another application instance currently owns this provider/dataset/league/season work unit",
                    0, "LOCK_BUSY", true);
        }
        return Objects.requireNonNull(result.get(), "Locked work-unit execution returned no result");
    }

    private IngestionUnitResult executeUnlocked(
            IngestionWorkUnit unit, boolean historical, Supplier<IngestionUnitResult> operation) {
        long startedAt = System.nanoTime();
        try {
            if (historical && checkpointStore.isSatisfied(
                    unit.provider(), unit.dataset(), unit.leagueId(), unit.season())) {
                return IngestionUnitResult.skipped(unit);
            }
            checkpointStore.markStatus(unit.provider(), unit.dataset(), unit.leagueId(),
                    unit.season(), CheckpointStatus.RUNNING);
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
            markFailureState(unit, CheckpointStatus.PARTIAL, exception);
            return IngestionUnitResult.partial(unit, exception.getMessage(), elapsedMillis(startedAt));
        } catch (ProviderDataException exception) {
            CheckpointStatus status = exception.isNotAvailable()
                    ? CheckpointStatus.NOT_AVAILABLE : CheckpointStatus.FAILED;
            markFailureState(unit, status, exception);
            if (exception.isNotAvailable()) {
                return IngestionUnitResult.notAvailable(unit, exception.getMessage(), elapsedMillis(startedAt));
            }
            return IngestionUnitResult.failed(unit, exception.getMessage(), elapsedMillis(startedAt),
                    exception.getFailureCode() == null ? "PROVIDER_DATA" : exception.getFailureCode(),
                    exception.isRetryable());
        } catch (ProviderTransportException exception) {
            markFailureState(unit, CheckpointStatus.FAILED, exception);
            return IngestionUnitResult.failed(unit, exception.getMessage(), elapsedMillis(startedAt),
                    "PROVIDER_TRANSPORT", true);
        } catch (DuplicateKeyException exception) {
            // A competing provider unit may have inserted the same external identity.
            // The next scheduled attempt re-reads the unique mapping before creating it again.
            markFailureState(unit, CheckpointStatus.FAILED, exception);
            return IngestionUnitResult.failed(unit, exception.getMessage(), elapsedMillis(startedAt),
                    "DATABASE_IDENTITY_CONFLICT", true);
        } catch (DataAccessResourceFailureException exception) {
            markFailureState(unit, CheckpointStatus.FAILED, exception);
            return IngestionUnitResult.failed(unit, exception.getMessage(), elapsedMillis(startedAt),
                    "DATABASE_CONNECTION", true);
        } catch (TransientDataAccessException exception) {
            markFailureState(unit, CheckpointStatus.FAILED, exception);
            return IngestionUnitResult.failed(unit, exception.getMessage(), elapsedMillis(startedAt),
                    "DATABASE_TRANSIENT", true);
        } catch (DataAccessException exception) {
            markFailureState(unit, CheckpointStatus.FAILED, exception);
            return IngestionUnitResult.failed(unit, exception.getMessage(), elapsedMillis(startedAt),
                    "DATABASE", false);
        } catch (TransactionException exception) {
            markFailureState(unit, CheckpointStatus.FAILED, exception);
            return IngestionUnitResult.failed(unit, exception.getMessage(), elapsedMillis(startedAt),
                    "DATABASE_TRANSACTION", true);
        } catch (RuntimeException exception) {
            markFailureState(unit, CheckpointStatus.FAILED, exception);
            return IngestionUnitResult.failed(unit, exception.getMessage(), elapsedMillis(startedAt),
                    exception.getClass().getSimpleName(), false);
        }
    }

    private static String lockScope(IngestionWorkUnit unit) {
        return "unit/" + unit.provider().name() + "/" + unit.dataset().name()
                + "/" + unit.leagueId() + "/" + unit.season();
    }

    private void markFailureState(IngestionWorkUnit unit, CheckpointStatus status, RuntimeException cause) {
        try {
            checkpointStore.markStatus(unit.provider(), unit.dataset(), unit.leagueId(), unit.season(), status);
        } catch (RuntimeException checkpointFailure) {
            cause.addSuppressed(checkpointFailure);
        }
    }

    private static IngestionUnitResult withDuration(IngestionUnitResult result, long durationMillis) {
        return new IngestionUnitResult(result.unit(), result.status(), result.fetched(), result.persisted(),
                result.skippedRows(), result.failedRows(), result.skipped(), durationMillis,
                result.message(), result.failureCode(), result.retryable());
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
