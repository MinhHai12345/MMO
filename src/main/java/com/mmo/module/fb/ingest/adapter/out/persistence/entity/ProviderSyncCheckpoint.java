package com.mmo.module.fb.ingest.adapter.out.persistence.entity;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.enums.CheckpointStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Durable marker for a completed provider/resource bootstrap.
 * A marker is written only after every required stage in the initial backfill succeeds.
 */
@Entity
@Table(name = "fb_provider_sync_checkpoints",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_fb_provider_sync_checkpoint",
                columnNames = {"provider", "checkpoint_key"}),
        indexes = @Index(name = "idx_fb_provider_sync_checkpoint_completed",
                columnList = "completed_at"))
@Getter
@Setter
public class ProviderSyncCheckpoint extends AbstractEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private Provider provider;

    @Column(name = "checkpoint_key", nullable = false, length = 64)
    private String checkpointKey;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    /**
     * Nullable for backwards compatibility: existing rows without a status and with a
     * completed_at timestamp are interpreted as COMPLETED.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "checkpoint_status", length = 24)
    private CheckpointStatus status;
}
