package com.mmo.module.fb.workflow.domain.entity;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.workflow.domain.enums.FootballWorkflowStage;
import com.mmo.module.fb.workflow.domain.enums.FootballWorkflowStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fb_workflow_runs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_fb_workflow_execution_id", columnNames = "execution_id"),
                @UniqueConstraint(name = "uk_fb_workflow_idempotency_key", columnNames = "idempotency_key")
        })
@Getter
@Setter
@NoArgsConstructor
public class FootballWorkflowRunEntity extends AbstractEntity {

    @Column(name = "execution_id", nullable = false, length = 36, updatable = false)
    private String executionId;

    @Column(name = "idempotency_key", nullable = false, length = 160, updatable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private FootballWorkflowStatus status = FootballWorkflowStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_stage", length = 32)
    private FootballWorkflowStage currentStage;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "failure_reason", length = 2000)
    private String failureReason;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private Long version;

    public static FootballWorkflowRunEntity create(String idempotencyKey) {
        FootballWorkflowRunEntity run = new FootballWorkflowRunEntity();
        run.executionId = UUID.randomUUID().toString();
        run.idempotencyKey = idempotencyKey;
        return run;
    }

    public void start(FootballWorkflowStage stage) {
        status = FootballWorkflowStatus.RUNNING;
        currentStage = stage;
        failureReason = null;
        completedAt = null;
        if (startedAt == null) startedAt = Instant.now();
    }

    public void complete() {
        status = FootballWorkflowStatus.COMPLETED;
        completedAt = Instant.now();
        failureReason = null;
    }

    public void fail(String reason) {
        status = FootballWorkflowStatus.FAILED;
        failureReason = reason;
        retryCount++;
        completedAt = Instant.now();
    }

    public void prepareForResume() {
        status = FootballWorkflowStatus.PENDING;
        failureReason = null;
        completedAt = null;
    }
}
