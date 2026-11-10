package com.mmo.module.fb.workflow.application.service;

import com.mmo.module.fb.workflow.domain.entity.FootballWorkflowRunEntity;
import com.mmo.module.fb.workflow.domain.entity.FootballWorkflowStageExecutionEntity;
import com.mmo.module.fb.workflow.domain.enums.FootballWorkflowStage;
import com.mmo.module.fb.workflow.domain.enums.FootballWorkflowStageStatus;
import com.mmo.module.fb.workflow.domain.enums.FootballWorkflowStatus;
import com.mmo.module.fb.workflow.infrastructure.persistence.repository.FootballWorkflowRunRepository;
import com.mmo.module.fb.workflow.infrastructure.persistence.repository.FootballWorkflowStageExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowCheckpointService {

    private static final Duration EXECUTION_LEASE = Duration.ofHours(6);

    private final FootballWorkflowRunRepository runRepository;
    private final FootballWorkflowStageExecutionRepository stageRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long getOrCreateRun(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Workflow idempotency key is required.");
        }
        return runRepository.findByIdempotencyKeyForUpdate(idempotencyKey)
                .map(FootballWorkflowRunEntity::getId)
                .orElseGet(() -> runRepository.saveAndFlush(
                        FootballWorkflowRunEntity.create(idempotencyKey)).getId());
    }

    /**
     * Claims a run for one coordinator. A new token fences off workers whose lease expired.
     * Returns null when the run is complete or another coordinator still owns a valid lease.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String claimRun(Long runId) {
        FootballWorkflowRunEntity run = runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow run not found: " + runId));
        if (run.getStatus() == FootballWorkflowStatus.COMPLETED) return null;

        Instant now = Instant.now();
        if (run.getStatus() == FootballWorkflowStatus.RUNNING
                && run.getLeaseUntil() != null && run.getLeaseUntil().isAfter(now)) return null;

        List<FootballWorkflowStageExecutionEntity> executions =
                stageRepository.findAllByWorkflowRunIdOrderByIdAsc(runId);
        executions.forEach(FootballWorkflowStageExecutionEntity::resetInterrupted);
        stageRepository.saveAll(executions);

        String token = UUID.randomUUID().toString();
        run.setStatus(FootballWorkflowStatus.RUNNING);
        run.setFailureReason(null);
        run.setCompletedAt(null);
        run.setLeaseUntil(now.plus(EXECUTION_LEASE));
        run.setExecutionToken(token);
        runRepository.save(run);
        return token;
    }

    /**
     * Extends the active execution lease. An expired lease cannot be revived by its previous owner,
     * even when no replacement coordinator has claimed it yet.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Instant renewLease(Long runId, String executionToken) {
        FootballWorkflowRunEntity run = lockAndValidateOwner(runId, executionToken);
        Instant renewedUntil = Instant.now().plus(EXECUTION_LEASE);
        run.setLeaseUntil(renewedUntil);
        runRepository.save(run);
        return renewedUntil;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseRun(Long runId, String executionToken) {
        FootballWorkflowRunEntity run = lockAndValidateOwner(runId, executionToken);
        if (run.getStatus() != FootballWorkflowStatus.COMPLETED) {
            List<FootballWorkflowStageExecutionEntity> executions =
                    stageRepository.findAllByWorkflowRunIdOrderByIdAsc(runId);
            executions.forEach(FootballWorkflowStageExecutionEntity::resetInterrupted);
            stageRepository.saveAll(executions);
            run.prepareForResume();
            runRepository.save(run);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean isRunCompleted(Long runId) {
        return runRepository.findById(runId)
                .map(run -> run.getStatus() == FootballWorkflowStatus.COMPLETED)
                .orElseThrow(() -> new IllegalArgumentException("Workflow run not found: " + runId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean isStageCompleted(Long runId, FootballWorkflowStage stage) {
        return stageRepository.findByWorkflowRunIdAndStage(runId, stage)
                .map(execution -> execution.getStatus() == FootballWorkflowStageStatus.COMPLETED)
                .orElse(false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void startStage(Long runId, FootballWorkflowStage stage, String executionToken) {
        FootballWorkflowRunEntity run = lockAndValidateOwner(runId, executionToken);
        FootballWorkflowStageExecutionEntity execution = stageRepository
                .findForUpdate(runId, stage)
                .orElseGet(() -> {
                    FootballWorkflowStageExecutionEntity created = new FootballWorkflowStageExecutionEntity();
                    created.setWorkflowRun(run);
                    created.setStage(stage);
                    return created;
                });
        if (execution.getStatus() == FootballWorkflowStageStatus.COMPLETED) {
            execution.setStatus(FootballWorkflowStageStatus.PENDING);
        }
        execution.start();
        run.start(stage);
        run.setLeaseUntil(Instant.now().plus(EXECUTION_LEASE));
        stageRepository.save(execution);
        runRepository.save(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeStage(Long runId, FootballWorkflowStage stage, String executionToken) {
        FootballWorkflowRunEntity run = lockAndValidateOwner(runId, executionToken);
        FootballWorkflowStageExecutionEntity execution = stageRepository.findForUpdate(runId, stage)
                .orElseThrow(() -> new IllegalStateException("Stage checkpoint does not exist: " + stage));
        execution.complete();
        stageRepository.save(execution);
        run.setCurrentStage(stage);
        run.setFailureReason(null);
        runRepository.save(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failStage(Long runId, FootballWorkflowStage stage, String reason, String executionToken) {
        FootballWorkflowRunEntity run = lockAndValidateOwner(runId, executionToken);
        stageRepository.findForUpdate(runId, stage).ifPresent(execution -> {
            execution.fail(reason);
            stageRepository.save(execution);
        });
        run.fail(reason);
        runRepository.save(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeRun(Long runId, String executionToken) {
        FootballWorkflowRunEntity run = lockAndValidateOwner(runId, executionToken);
        run.complete();
        runRepository.save(run);
    }

    private FootballWorkflowRunEntity lockAndValidateOwner(Long runId, String executionToken) {
        FootballWorkflowRunEntity run = runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow run not found: " + runId));
        if (executionToken == null || !executionToken.equals(run.getExecutionToken())
                || run.getStatus() != FootballWorkflowStatus.RUNNING
                || run.getLeaseUntil() == null || !run.getLeaseUntil().isAfter(Instant.now())) {
            throw new IllegalStateException("Workflow execution lease is no longer owned by this coordinator.");
        }
        return run;
    }
}
