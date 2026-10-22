package com.mmo.module.fb.analytics.service;

import com.mmo.module.fb.analytics.service.AnalyticsDataReadinessService.DataReadinessReport;
import com.mmo.module.fb.analytics.service.DataCoverageAuditService.DataCoverageAuditReport;
import com.mmo.module.fb.analytics.service.TeamFormBackTestService.TeamFormBackTestReport;
import com.mmo.module.fb.analytics.service.WalkForwardBackTestService.BackTestReport;

import java.time.Instant;
import java.util.List;

public interface AnalyticsWorkflowService {

    AnalyticsWorkflowReport run(Instant fromInclusive, Instant toExclusive);

    record AnalyticsWorkflowReport(
            Instant generatedAt,
            Instant from,
            Instant to,
            String status,
            List<WorkflowStep> steps,
            DataCoverageAuditReport coverage,
            DataReadinessReport readiness,
            TeamFormBackTestReport teamFormBacktest,
            BackTestReport predictionWalkForwardBacktest) {
        public AnalyticsWorkflowReport {
            steps = steps == null ? List.of() : List.copyOf(steps);
        }
    }

    record WorkflowStep(String name, String status, String message) {
    }
}
