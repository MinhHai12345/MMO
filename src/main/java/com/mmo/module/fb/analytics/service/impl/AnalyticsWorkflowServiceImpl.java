package com.mmo.module.fb.analytics.service.impl;

import com.mmo.module.fb.analytics.service.AnalyticsDataReadinessService;
import com.mmo.module.fb.analytics.service.AnalyticsWorkflowService;
import com.mmo.module.fb.analytics.service.DataCoverageAuditService;
import com.mmo.module.fb.analytics.service.TeamFormBackTestService;
import com.mmo.module.fb.analytics.service.WalkForwardBackTestService;
import com.mmo.module.fb.analytics.service.AnalyticsWorkflowService.AnalyticsWorkflowReport;
import com.mmo.module.fb.analytics.service.AnalyticsWorkflowService.WorkflowStep;
import com.mmo.module.fb.analytics.service.DataCoverageAuditService.DataCoverageAuditReport;
import com.mmo.module.fb.analytics.service.TeamFormBackTestService.TeamFormBackTestReport;
import com.mmo.module.fb.analytics.service.WalkForwardBackTestService.BackTestReport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsWorkflowServiceImpl implements AnalyticsWorkflowService {

    private final DataCoverageAuditService coverageAuditService;
    private final AnalyticsDataReadinessService readinessService;
    private final TeamFormBackTestService teamFormBackTestService;
    private final WalkForwardBackTestService walkForwardBackTestService;

    @Override
    @Transactional(readOnly = true)
    public AnalyticsWorkflowReport run(Instant fromInclusive, Instant toExclusive) {
        if (fromInclusive == null || toExclusive == null || !fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("fromInclusive must be before toExclusive");
        }

        List<WorkflowStep> steps = new ArrayList<>();
        DataCoverageAuditReport coverage = coverageAuditService.runAudit();
        steps.add(new WorkflowStep("DATA_COVERAGE_AUDIT",
                "CRITICAL".equals(coverage.status()) ? "FAILED" : "COMPLETED",
                "Audit persisted fixture, score, duplicate and provider-mapping coverage."));

        var readiness = readinessService.getReadiness();
        steps.add(new WorkflowStep("DATA_READINESS", "COMPLETED",
                "Readiness is informational for feature availability; missing xG does not block the goals-only baseline."));

        var totals = coverage.totals();
        boolean blocked = "CRITICAL".equals(coverage.status())
                || totals.finishedMissingScores() > 0
                || totals.duplicateFixtureGroups() > 0;
        if (blocked) {
            steps.add(new WorkflowStep("TEAM_FORM_BACKTEST", "SKIPPED",
                    "Resolve critical coverage findings before evaluating the model."));
            steps.add(new WorkflowStep("PREDICTION_WALK_FORWARD_BACKTEST", "SKIPPED",
                    "Resolve critical coverage findings before evaluating persisted predictions."));
            return new AnalyticsWorkflowReport(Instant.now(), fromInclusive, toExclusive,
                    "BLOCKED_DATA_QUALITY", steps, coverage, readiness, null, null);
        }

        TeamFormBackTestReport teamForm = teamFormBackTestService.run(fromInclusive, toExclusive);
        steps.add(new WorkflowStep("TEAM_FORM_BACKTEST", stepStatus(teamForm.status()),
                "Evaluates the point-in-time team-form baseline across every eligible finished fixture in [from, to)."));

        BackTestReport predictionWalkForward = walkForwardBackTestService.run(fromInclusive, toExclusive);
        steps.add(new WorkflowStep("PREDICTION_WALK_FORWARD_BACKTEST", stepStatus(predictionWalkForward.status()),
                "Evaluates persisted pre-kickoff predictions with historical outcomes matured using a conservative lag."));

        boolean limited = "WARNING".equals(coverage.status())
                || !"OK".equals(teamForm.status())
                || !"OK".equals(predictionWalkForward.status());
        return new AnalyticsWorkflowReport(Instant.now(), fromInclusive, toExclusive,
                limited ? "COMPLETED_WITH_LIMITATIONS" : "COMPLETED",
                steps, coverage, readiness, teamForm, predictionWalkForward);
    }

    private String stepStatus(String status) {
        return "OK".equals(status) ? "COMPLETED" : "COMPLETED_WITH_LIMITATIONS";
    }
}
