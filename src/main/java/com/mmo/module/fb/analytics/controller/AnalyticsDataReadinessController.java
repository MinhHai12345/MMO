package com.mmo.module.fb.analytics.controller;

import com.mmo.module.fb.analytics.service.AnalyticsDataReadinessService;
import com.mmo.module.fb.analytics.service.AnalyticsWorkflowService;
import com.mmo.module.fb.analytics.service.AnalyticsWorkflowService.AnalyticsWorkflowReport;
import com.mmo.module.fb.analytics.service.AnalyticsDataReadinessService.DataReadinessReport;
import com.mmo.module.fb.analytics.service.DataCoverageAuditService;
import com.mmo.module.fb.analytics.service.DataCoverageAuditService.DataCoverageAuditReport;
import com.mmo.module.fb.analytics.service.TeamFormBackTestService;
import com.mmo.module.fb.analytics.service.TeamFormBackTestService.TeamFormBackTestReport;
import com.mmo.module.fb.analytics.service.WalkForwardBackTestService;
import com.mmo.module.fb.analytics.service.WalkForwardBackTestService.BackTestReport;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;

/** Read-only operational endpoints for inspecting analytics data coverage. */
@RestController
@RequestMapping("/api/fb/analytics")
@RequiredArgsConstructor
public class AnalyticsDataReadinessController {
    private final AnalyticsDataReadinessService analyticsDataReadinessService;
    private final AnalyticsWorkflowService analyticsWorkflowService;
    private final DataCoverageAuditService dataCoverageAuditService;
    private final TeamFormBackTestService teamFormBackTestService;
    private final WalkForwardBackTestService walkForwardBackTestService;

    @GetMapping("/data-readiness")
    public DataReadinessReport getDataReadiness() {
        return analyticsDataReadinessService.getReadiness();
    }

    /**
     * Runs the read-only analytics workflow in order: coverage audit, readiness,
     * point-in-time team-form benchmark, then persisted-prediction walk-forward evaluation.
     * The backtests are skipped when critical data-quality findings remain.
     */
    @GetMapping("/workflow")
    public AnalyticsWorkflowReport runWorkflow(@RequestParam Instant from, @RequestParam Instant to) {
        return analyticsWorkflowService.run(from, to);
    }

    /** Runs a read-only audit of persisted coverage, scores, standings, duplicates and provider mappings. */
    @GetMapping("/data-coverage-audit")
    public DataCoverageAuditReport getDataCoverageAudit() {
        return dataCoverageAuditService.runAudit();
    }

    @GetMapping("/team-form-backtest")
    public TeamFormBackTestReport runTeamFormBackTest(@RequestParam Instant from, @RequestParam Instant to) {
        return teamFormBackTestService.run(from, to);
    }

    /** Dates are ISO-8601 instants; start is inclusive and end is exclusive. */
    @GetMapping("/walk-forward-backtest")
    public BackTestReport runWalkForwardBackTest(@RequestParam Instant from, @RequestParam Instant to) {
        return walkForwardBackTestService.run(from, to);
    }
}
