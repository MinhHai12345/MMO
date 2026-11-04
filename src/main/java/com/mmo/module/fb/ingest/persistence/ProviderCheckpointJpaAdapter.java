package com.mmo.module.fb.ingest.persistence;

import com.mmo.module.fb.ingest.application.port.out.ProviderCheckpointStore;
import com.mmo.module.fb.ingest.entity.ProviderSyncCheckpoint;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.repository.ProviderSyncCheckpointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** JPA adapter for durable provider/resource checkpoints. */
@Component
@RequiredArgsConstructor
public class ProviderCheckpointJpaAdapter implements ProviderCheckpointStore {

    private final ProviderSyncCheckpointRepository repository;

    @Override
    public boolean isCompleted(Provider provider, String checkpointKey) {
        return repository.existsByProviderAndCheckpointKey(provider, checkpointKey);
    }

    @Override
    public void markCompleted(Provider provider, String checkpointKey) {
        ProviderSyncCheckpoint checkpoint = repository.findByProviderAndCheckpointKey(provider, checkpointKey)
                .orElseGet(ProviderSyncCheckpoint::new);
        checkpoint.setProvider(provider);
        checkpoint.setCheckpointKey(checkpointKey);
        checkpoint.setCompletedAt(Instant.now());
        repository.save(checkpoint);
    }
}
