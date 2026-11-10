package com.mmo.module.fb.workflow.domain.entity;

import com.mmo.module.fb.workflow.domain.enums.FootballWorkflowStage;
import com.mmo.module.fb.workflow.domain.enums.FootballWorkflowStageStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FootballWorkflowStageExecutionEntityTest {

    @Test
    void completedStageMustRemainCompletedWhenStartedAgain() {
        FootballWorkflowStageExecutionEntity execution = new FootballWorkflowStageExecutionEntity();
        execution.setStage(FootballWorkflowStage.DATA_INGESTION);

        execution.start();
        execution.complete();
        execution.start();

        assertThat(execution.getStatus()).isEqualTo(FootballWorkflowStageStatus.COMPLETED);
        assertThat(execution.getRetryCount()).isZero();
    }

    @Test
    void failedStageMustIncrementRetryCountWhenRetried() {
        FootballWorkflowStageExecutionEntity execution = new FootballWorkflowStageExecutionEntity();
        execution.setStage(FootballWorkflowStage.DATA_READINESS);

        execution.start();
        execution.fail("Readiness threshold not met.");
        execution.start();

        assertThat(execution.getStatus()).isEqualTo(FootballWorkflowStageStatus.RUNNING);
        assertThat(execution.getRetryCount()).isEqualTo(1);
        assertThat(execution.getFailureReason()).isNull();
    }

    @Test
    void interruptedStageMustReturnToPendingForResume() {
        FootballWorkflowStageExecutionEntity execution = new FootballWorkflowStageExecutionEntity();
        execution.setStage(FootballWorkflowStage.PREDICTIONS);
        execution.start();

        execution.resetInterrupted();

        assertThat(execution.getStatus()).isEqualTo(FootballWorkflowStageStatus.PENDING);
        assertThat(execution.getFailureReason()).contains("Previous execution ended");
    }
}
