package com.mmo.module.fb.ingest.persistence;

import com.mmo.module.fb.ingest.application.port.out.ProviderCheckpointStore;
import com.mmo.module.fb.ingest.entity.ProviderSyncCheckpoint;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.enums.CheckpointStatus;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.ingest.repository.ProviderSyncCheckpointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** JPA adapter for durable provider/resource checkpoints. */
@Component
@RequiredArgsConstructor
public class ProviderCheckpointJpaAdapter implements ProviderCheckpointStore {

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
        return isCompleted(provider, ProviderCheckpointStore.checkpointKey(dataset, leagueId, season));
    }

    @Override
    @Transactional
    public void markStatus(Provider provider, Dataset dataset, Long leagueId,
                           String season, CheckpointStatus status) {
        markStatus(provider, ProviderCheckpointStore.checkpointKey(dataset, leagueId, season), status);
    }

    private void markStatus(Provider provider, String checkpointKey, CheckpointStatus status) {
        ProviderSyncCheckpoint checkpoint = repository.findByProviderAndCheckpointKey(provider, checkpointKey)
                .orElseGet(ProviderSyncCheckpoint::new);
        checkpoint.setProvider(provider);
        checkpoint.setCheckpointKey(checkpointKey);
        checkpoint.setStatus(status);
        // The legacy timestamp is retained as the last state-transition timestamp.
        checkpoint.setCompletedAt(Instant.now());
        repository.save(checkpoint);
    }

    @Override
    @Transactional
    public void markCompleted(Provider provider, String checkpointKey) {
        markStatus(provider, checkpointKey, CheckpointStatus.COMPLETED);
    }
}
