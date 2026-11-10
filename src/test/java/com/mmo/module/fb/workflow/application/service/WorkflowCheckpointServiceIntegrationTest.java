package com.mmo.module.fb.workflow.application.service;

import com.mmo.module.fb.workflow.domain.entity.FootballWorkflowRunEntity;
import com.mmo.module.fb.workflow.domain.entity.FootballWorkflowStageExecutionEntity;
import com.mmo.module.fb.workflow.domain.enums.FootballWorkflowStage;
import com.mmo.module.fb.workflow.domain.enums.FootballWorkflowStageStatus;
import com.mmo.module.fb.workflow.domain.enums.FootballWorkflowStatus;
import com.mmo.module.fb.workflow.infrastructure.persistence.repository.FootballWorkflowRunRepository;
import com.mmo.module.fb.workflow.infrastructure.persistence.repository.FootballWorkflowStageExecutionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@EntityScan(basePackageClasses = {FootballWorkflowRunEntity.class, FootballWorkflowStageExecutionEntity.class})
@EnableJpaRepositories(basePackages = "com.mmo.module.fb.workflow.infrastructure.persistence.repository")
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
    void persistsStageCompletionIndependentlyAndReusesIdempotencyKey() {
        Long runId = checkpointService.getOrCreateRun("integration-workflow-2026-11-10");
        Long sameRunId = checkpointService.getOrCreateRun("integration-workflow-2026-11-10");
        String token = checkpointService.claimRun(runId);

        assertThat(sameRunId).isEqualTo(runId);
        assertThat(token).isNotBlank();
        checkpointService.startStage(runId, FootballWorkflowStage.DATA_INGESTION, token);
        checkpointService.completeStage(runId, FootballWorkflowStage.DATA_INGESTION, token);

        assertThat(checkpointService.isStageCompleted(runId, FootballWorkflowStage.DATA_INGESTION)).isTrue();
        assertThat(stageRepository.findByWorkflowRunIdAndStage(runId, FootballWorkflowStage.DATA_INGESTION))
                .get().extracting("status").isEqualTo(FootballWorkflowStageStatus.COMPLETED);
        assertThat(runRepository.findById(runId).orElseThrow().getStatus())
                .isEqualTo(FootballWorkflowStatus.RUNNING);
        checkpointService.completeRun(runId, token);
    }

    @Test
    void resumeResetsOnlyInterruptedStagesAndPreservesCompletedStages() {
        Long runId = checkpointService.getOrCreateRun("resume-workflow-2026-11-10");
        String token = checkpointService.claimRun(runId);
        checkpointService.startStage(runId, FootballWorkflowStage.DATA_INGESTION, token);
        checkpointService.completeStage(runId, FootballWorkflowStage.DATA_INGESTION, token);
        checkpointService.startStage(runId, FootballWorkflowStage.DATA_READINESS, token);

        checkpointService.releaseRun(runId, token);

        assertThat(checkpointService.isStageCompleted(runId, FootballWorkflowStage.DATA_INGESTION)).isTrue();
        assertThat(stageRepository.findByWorkflowRunIdAndStage(runId, FootballWorkflowStage.DATA_READINESS))
                .get().extracting("status").isEqualTo(FootballWorkflowStageStatus.PENDING);
        assertThat(runRepository.findById(runId).orElseThrow().getStatus())
                .isEqualTo(FootballWorkflowStatus.PENDING);
    }

    @Test
    void activeLeaseBlocksAnotherCoordinatorAndTokenChangesAfterRelease() {
        Long runId = checkpointService.getOrCreateRun("lease-workflow-2026-11-10");
        String firstToken = checkpointService.claimRun(runId);

        assertThat(firstToken).isNotBlank();
        assertThat(checkpointService.claimRun(runId)).isNull();

        checkpointService.releaseRun(runId, firstToken);
        String secondToken = checkpointService.claimRun(runId);

        assertThat(secondToken).isNotBlank().isNotEqualTo(firstToken);
        checkpointService.releaseRun(runId, secondToken);
    }
}
