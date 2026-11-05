package com.mmo.module.fb.ingest.adapter.out.persistence.repository;

import com.mmo.module.fb.ingest.adapter.out.persistence.entity.ProviderSyncCheckpoint;
import com.mmo.module.fb.ingest.enums.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProviderSyncCheckpointRepository extends JpaRepository<ProviderSyncCheckpoint, Long> {
    Optional<ProviderSyncCheckpoint> findByProviderAndCheckpointKey(Provider provider, String checkpointKey);
    boolean existsByProviderAndCheckpointKey(Provider provider, String checkpointKey);
}
