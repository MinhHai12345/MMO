package com.mmo.module.fb.ingest.adapter.out.persistence.core;

import com.mmo.module.fb.ingest.adapter.out.persistence.entity.ProviderSyncCheckpoint;
import com.mmo.module.fb.ingest.adapter.out.persistence.repository.ProviderSyncCheckpointRepository;
import com.mmo.module.fb.ingest.application.model.IngestionCheckpointProgress;
import com.mmo.module.fb.ingest.application.port.out.ProviderCheckpointPort;
import com.mmo.module.fb.ingest.enums.CheckpointStatus;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.Dataset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/** JPA adapter for durable provider/resource checkpoints and resumable batch progress. */
@Component
@RequiredArgsConstructor
public class ProviderCheckpointJpaAdapter implements ProviderCheckpointPort {

    private final ProviderSyncCheckpointRepository repository;

    @Override
    @Transactional(readOnly = true)
    public boolean isCompleted(Provider provider, String checkpointKey) {
        return repository.findByProviderAndCheckpointKey(provider, checkpointKey)
                .map(checkpoint -> checkpoint.getStatus() == null
                        || checkpoint.getStatus() == CheckpointStatus.COMPLETED)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCompleted(Provider provider, Dataset dataset, Long leagueId, String season) {
        return isCompleted(provider, ProviderCheckpointPort.checkpointKey(dataset, leagueId, season));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSatisfied(Provider provider, Dataset dataset, Long leagueId, String season) {
        return repository.findByProviderAndCheckpointKey(
                        provider, ProviderCheckpointPort.checkpointKey(dataset, leagueId, season))
                .map(checkpoint -> checkpoint.getStatus() == null
                        || checkpoint.getStatus() == CheckpointStatus.COMPLETED
                        || checkpoint.getStatus() == CheckpointStatus.NOT_AVAILABLE)
                .orElse(false);
    }

    @Override
    @Transactional
    public void markStatus(Provider provider, Dataset dataset, Long leagueId,
                           String season, CheckpointStatus status) {
        markStatus(provider, ProviderCheckpointPort.checkpointKey(dataset, leagueId, season), status);
    }

    private void markStatus(Provider provider, String checkpointKey, CheckpointStatus status) {
        if (status == null) throw new IllegalArgumentException("Checkpoint status must not be null");
        ProviderSyncCheckpoint checkpoint = repository.findByProviderAndCheckpointKey(provider, checkpointKey)
                .orElseGet(ProviderSyncCheckpoint::new);
        checkpoint.setProvider(provider);
        checkpoint.setCheckpointKey(checkpointKey);
        checkpoint.setStatus(status);
        checkpoint.setCompletedAt(Instant.now());
        repository.save(checkpoint);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IngestionCheckpointProgress> findProgress(
            Provider provider, Dataset dataset, Long leagueId, String season) {
        return repository.findByProviderAndCheckpointKey(
                        provider, ProviderCheckpointPort.checkpointKey(dataset, leagueId, season))
                .filter(checkpoint -> checkpoint.getCursorValue() != null)
                .map(checkpoint -> new IngestionCheckpointProgress(
                        checkpoint.getCursorValue(),
                        checkpoint.getProcessedRows(),
                        checkpoint.getPersistedRows(),
                        checkpoint.getFailedRows(),
                        checkpoint.getProgressUpdatedAt()));
    }

    @Override
    @Transactional
    public void saveProgress(Provider provider, Dataset dataset, Long leagueId, String season,
                             String cursor, long processedRows, long persistedRows, long failedRows) {
        if (cursor == null || cursor.isBlank() || cursor.length() > 1024) {
            throw new IllegalArgumentException("Checkpoint cursor must contain 1..1024 characters");
        }
        if (processedRows < 0 || persistedRows < 0 || failedRows < 0) {
            throw new IllegalArgumentException("Checkpoint progress counters must be non-negative");
        }
        String checkpointKey = ProviderCheckpointPort.checkpointKey(dataset, leagueId, season);
        ProviderSyncCheckpoint checkpoint = repository.findByProviderAndCheckpointKey(provider, checkpointKey)
                .orElseGet(ProviderSyncCheckpoint::new);
        checkpoint.setProvider(provider);
        checkpoint.setCheckpointKey(checkpointKey);
        if (checkpoint.getStatus() == null) checkpoint.setStatus(CheckpointStatus.RUNNING);
        checkpoint.setCursorValue(cursor);
        checkpoint.setProcessedRows(processedRows);
        checkpoint.setPersistedRows(persistedRows);
        checkpoint.setFailedRows(failedRows);
        checkpoint.setProgressUpdatedAt(Instant.now());
        if (checkpoint.getCompletedAt() == null) checkpoint.setCompletedAt(Instant.now());
        repository.save(checkpoint);
    }

    @Override
    @Transactional
    public void clearProgress(Provider provider, Dataset dataset, Long leagueId, String season) {
        repository.findByProviderAndCheckpointKey(
                        provider, ProviderCheckpointPort.checkpointKey(dataset, leagueId, season))
                .ifPresent(checkpoint -> {
                    checkpoint.setCursorValue(null);
                    checkpoint.setProcessedRows(0);
                    checkpoint.setPersistedRows(0);
                    checkpoint.setFailedRows(0);
                    checkpoint.setProgressUpdatedAt(null);
                    repository.save(checkpoint);
                });
    }

    @Override
    @Transactional
    public void markCompleted(Provider provider, String checkpointKey) {
        markStatus(provider, checkpointKey, CheckpointStatus.COMPLETED);
    }
}
