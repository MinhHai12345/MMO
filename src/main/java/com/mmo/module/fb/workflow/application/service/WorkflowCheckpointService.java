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

@Service
@RequiredArgsConstructor
public class WorkflowCheckpointService {

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


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimRun(Long runId) {
        FootballWorkflowRunEntity run = runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow run not found: " + runId));
        if (run.getStatus() == FootballWorkflowStatus.COMPLETED) return false;
        Instant now = Instant.now();
        if (run.getStatus() == FootballWorkflowStatus.RUNNING
                && run.getLeaseUntil() != null && run.getLeaseUntil().isAfter(now)) return false;
        List<FootballWorkflowStageExecutionEntity> executions =
                stageRepository.findAllByWorkflowRunIdOrderByIdAsc(runId);
        executions.forEach(FootballWorkflowStageExecutionEntity::resetInterrupted);
        stageRepository.saveAll(executions);
        run.setStatus(FootballWorkflowStatus.RUNNING);
        run.setFailureReason(null);
        run.setCompletedAt(null);
        run.setLeaseUntil(now.plus(Duration.ofHours(6)));
        runRepository.save(run);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseRun(Long runId) {
        FootballWorkflowRunEntity run = runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow run not found: " + runId));
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
    public void startStage(Long runId, FootballWorkflowStage stage) {
        FootballWorkflowRunEntity run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow run not found: " + runId));
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
        stageRepository.save(execution);
        runRepository.save(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeStage(Long runId, FootballWorkflowStage stage) {
        FootballWorkflowRunEntity run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow run not found: " + runId));
        FootballWorkflowStageExecutionEntity execution = stageRepository.findForUpdate(runId, stage)
                .orElseThrow(() -> new IllegalStateException("Stage checkpoint does not exist: " + stage));
        execution.complete();
        stageRepository.save(execution);
        run.setCurrentStage(stage);
        run.setFailureReason(null);
        runRepository.save(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failStage(Long runId, FootballWorkflowStage stage, String reason) {
        FootballWorkflowRunEntity run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow run not found: " + runId));
        stageRepository.findForUpdate(runId, stage).ifPresent(execution -> {
            execution.fail(reason);
            stageRepository.save(execution);
        });
        run.fail(reason);
        runRepository.save(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeRun(Long runId) {
        FootballWorkflowRunEntity run = runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow run not found: " + runId));
        run.complete();
        runRepository.save(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void prepareForResume(Long runId) {
        FootballWorkflowRunEntity run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow run not found: " + runId));
        if (run.getStatus() == FootballWorkflowStatus.COMPLETED) {
            return;
        }
        List<FootballWorkflowStageExecutionEntity> executions =
                stageRepository.findAllByWorkflowRunIdOrderByIdAsc(runId);
        executions.forEach(FootballWorkflowStageExecutionEntity::resetInterrupted);
        stageRepository.saveAll(executions);
        run.prepareForResume();
        runRepository.save(run);
    }
}
