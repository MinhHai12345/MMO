package com.mmo.module.fb.workflow.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.module.fb.workflow.application.port.in.FootballWorkflowUseCase;
import com.mmo.module.fb.workflow.application.port.in.FootballWorkflowUseCase.WorkflowReport;
import com.mmo.module.fb.workflow.application.port.in.FootballWorkflowUseCase.WorkflowStep;
import org.junit.jupiter.api.Test;
import org.quartz.JobExecutionContext;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FootballWorkflowOrchestrationQuartzJobTest {

    @Test
    void delegatesToCoordinatorUsingUtcWorkflowDateAndQuartzStopSignal() {
        FootballWorkflowUseCase useCase = mock(FootballWorkflowUseCase.class);
        FootballWorkflowOrchestrationQuartzJob job = new FootballWorkflowOrchestrationQuartzJob(useCase);
        JobExecutionContext context = mock(JobExecutionContext.class);
        CronJob cronJob = mock(CronJob.class);
        LocalDate expectedDate = LocalDate.now(ZoneOffset.UTC);

        when(useCase.run(eq(expectedDate), any(BooleanSupplier.class)))
                .thenReturn(report("COMPLETED"));

        assertDoesNotThrow(() -> job.executeInternal(context, cronJob));

        verify(useCase).run(eq(expectedDate), any(BooleanSupplier.class));
    }

    @Test
    void treatsInterruptedWorkflowAsNonFailure() {
        FootballWorkflowUseCase useCase = mock(FootballWorkflowUseCase.class);
        FootballWorkflowOrchestrationQuartzJob job = new FootballWorkflowOrchestrationQuartzJob(useCase);
        when(useCase.run(any(LocalDate.class), any(BooleanSupplier.class)))
                .thenReturn(report("INTERRUPTED"));

        assertDoesNotThrow(() -> job.executeInternal(mock(JobExecutionContext.class), mock(CronJob.class)));
    }

    @Test
    void propagatesFailedWorkflowToQuartzExecutionState() {
        FootballWorkflowUseCase useCase = mock(FootballWorkflowUseCase.class);
        FootballWorkflowOrchestrationQuartzJob job = new FootballWorkflowOrchestrationQuartzJob(useCase);
        when(useCase.run(any(LocalDate.class), any(BooleanSupplier.class)))
                .thenReturn(report("FAILED"));

        assertThrows(IllegalStateException.class,
                () -> job.executeInternal(mock(JobExecutionContext.class), mock(CronJob.class)));
    }

    private WorkflowReport report(String status) {
        Instant now = Instant.now();
        return new WorkflowReport(status, LocalDate.now(ZoneOffset.UTC), now, now,
                List.<WorkflowStep>of(), null, 0, 0);
    }
}
