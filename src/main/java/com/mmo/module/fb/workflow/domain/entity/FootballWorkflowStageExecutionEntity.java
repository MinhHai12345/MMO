package com.mmo.module.fb.workflow.domain.entity;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.workflow.domain.enums.FootballWorkflowStage;
import com.mmo.module.fb.workflow.domain.enums.FootballWorkflowStageStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "fb_workflow_stage_executions",
        uniqueConstraints = @UniqueConstraint(name = "uk_fb_workflow_run_stage",
                columnNames = {"workflow_run_id", "stage"}))
@Getter
@Setter
@NoArgsConstructor
public class FootballWorkflowStageExecutionEntity extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_run_id", nullable = false)
    private FootballWorkflowRunEntity workflowRun;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FootballWorkflowStage stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private FootballWorkflowStageStatus status = FootballWorkflowStageStatus.PENDING;

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

    public void start() {
        if (status == FootballWorkflowStageStatus.COMPLETED) return;
        if (status == FootballWorkflowStageStatus.FAILED) retryCount++;
        status = FootballWorkflowStageStatus.RUNNING;
        failureReason = null;
        completedAt = null;
        startedAt = Instant.now();
    }

    public void complete() {
        status = FootballWorkflowStageStatus.COMPLETED;
        completedAt = Instant.now();
        failureReason = null;
    }

    public void fail(String reason) {
        status = FootballWorkflowStageStatus.FAILED;
        failureReason = reason;
        completedAt = Instant.now();
    }

    public void resetInterrupted() {
        if (status == FootballWorkflowStageStatus.RUNNING) {
            retryCount++;
            status = FootballWorkflowStageStatus.PENDING;
            failureReason = "Previous execution ended before stage completion.";
            completedAt = null;
        }
    }
}
