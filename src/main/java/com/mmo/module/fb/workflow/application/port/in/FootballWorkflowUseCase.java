package com.mmo.module.fb.workflow.application.port.in;

import java.time.LocalDate;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Application use case for the end-to-end football data-to-content workflow. */
public interface FootballWorkflowUseCase {

    WorkflowReport runDaily();

    WorkflowReport run(LocalDate utcDate, BooleanSupplier stopRequested);

    record WorkflowReport(
            String status,
            LocalDate workflowDate,
            java.time.Instant startedAt,
            java.time.Instant completedAt,
            List<WorkflowStep> steps,
            Long contentRunId,
            int draftsGenerated,
            int contentFailures) {
        public WorkflowReport {
            steps = steps == null ? List.of() : List.copyOf(steps);
        }
    }

    record WorkflowStep(
            String name,
            String status,
            long durationMillis,
            String message) {
    }
}
