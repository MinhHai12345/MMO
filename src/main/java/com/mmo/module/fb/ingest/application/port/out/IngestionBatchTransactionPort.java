package com.mmo.module.fb.ingest.application.port.out;

import java.util.function.Supplier;

/**
 * Defines the atomic boundary for one persisted ingestion batch.
 *
 * <p>All persistence adapters and checkpoint writes invoked by the callback must use
 * the same Spring transaction manager and REQUIRED propagation so that data and
 * the corresponding resume cursor commit or roll back together.</p>
 */
public interface IngestionBatchTransactionPort {

    <T> T execute(Supplier<T> work);

    default void executeWithoutResult(Runnable work) {
        execute(() -> {
            work.run();
            return null;
        });
    }
}