package com.mmo.module.fb.workflow.application.service;

import com.mmo.module.fb.workflow.domain.enums.FootballWorkflowStage;
import com.mmo.module.fb.workflow.domain.enums.FootballWorkflowStageStatus;
import com.mmo.module.fb.workflow.domain.enums.FootballWorkflowStatus;
import com.mmo.module.fb.workflow.infrastructure.persistence.repository.FootballWorkflowRunRepository;
import com.mmo.module.fb.workflow.infrastructure.persistence.repository.FootballWorkflowStageExecutionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(WorkflowCheckpointService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WorkflowCheckpointServiceIntegrationTest {

    @Autowired
    private WorkflowCheckpointService checkpointService;
    @Autowired
    private FootballWorkflowRunRepository runRepository;
    @Autowired
    private FootballWorkflowStageExecutionRepository stageRepository;

    @Test
    @Rollback
    void persistsStageCompletionIndependentlyAndReusesIdempotencyKey() {
        Long runId = checkpointService.getOrCreateRun("integration-workflow-2026-11-10");
        Long sameRunId = checkpointService.getOrCreateRun("integration-workflow-2026-11-10");

        assertThat(sameRunId).isEqualTo(runId);
        checkpointService.startStage(runId, FootballWorkflowStage.DATA_INGESTION);
        checkpointService.completeStage(runId, FootballWorkflowStage.DATA_INGESTION);

        assertThat(checkpointService.isStageCompleted(runId, FootballWorkflowStage.DATA_INGESTION)).isTrue();
        assertThat(stageRepository.findByWorkflowRunIdAndStage(runId, FootballWorkflowStage.DATA_INGESTION))
                .get().extracting("status").isEqualTo(FootballWorkflowStageStatus.COMPLETED);
        assertThat(runRepository.findById(runId).orElseThrow().getStatus())
                .isEqualTo(FootballWorkflowStatus.RUNNING);
    }

    @Test
    void resumeResetsOnlyInterruptedStagesAndPreservesCompletedStages() {
        Long runId = checkpointService.getOrCreateRun("resume-workflow-2026-11-10");
        checkpointService.startStage(runId, FootballWorkflowStage.DATA_INGESTION);
        checkpointService.completeStage(runId, FootballWorkflowStage.DATA_INGESTION);
        checkpointService.startStage(runId, FootballWorkflowStage.DATA_READINESS);

        checkpointService.prepareForResume(runId);

        assertThat(checkpointService.isStageCompleted(runId, FootballWorkflowStage.DATA_INGESTION)).isTrue();
        assertThat(stageRepository.findByWorkflowRunIdAndStage(runId, FootballWorkflowStage.DATA_READINESS))
                .get().extracting("status").isEqualTo(FootballWorkflowStageStatus.PENDING);
        assertThat(runRepository.findById(runId).orElseThrow().getStatus())
                .isEqualTo(FootballWorkflowStatus.PENDING);
    }
}
