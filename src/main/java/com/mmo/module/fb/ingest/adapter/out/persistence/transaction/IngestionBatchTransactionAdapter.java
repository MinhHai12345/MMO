package com.mmo.module.fb.ingest.adapter.out.persistence.transaction;

import com.mmo.module.fb.ingest.application.port.out.IngestionBatchTransactionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Executes each ingestion batch in a short REQUIRED transaction.
 * Provider network calls must happen before entering this boundary.
 */
@Component
@RequiredArgsConstructor
public class IngestionBatchTransactionAdapter implements IngestionBatchTransactionPort {

    private final PlatformTransactionManager transactionManager;

    @Override
    public <T> T execute(Supplier<T> work) {
        Objects.requireNonNull(work, "work must not be null");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> work.get());
    }
}