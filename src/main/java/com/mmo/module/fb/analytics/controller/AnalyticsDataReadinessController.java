package com.mmo.module.fb.analytics.controller;

import com.mmo.module.fb.analytics.service.AnalyticsDataReadinessService;
import com.mmo.module.fb.analytics.service.AnalyticsDataReadinessService.DataReadinessReport;
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

/**
 * Read-only operational endpoint for inspecting analytics data coverage.
 */
@RestController
@RequestMapping("/api/fb/analytics")
@RequiredArgsConstructor
public class AnalyticsDataReadinessController {

    private final AnalyticsDataReadinessService analyticsDataReadinessService;
    private final TeamFormBackTestService teamFormBackTestService;
    private final WalkForwardBackTestService walkForwardBackTestService;

    @GetMapping("/data-readiness")
    public DataReadinessReport getDataReadiness() {
        return analyticsDataReadinessService.getReadiness();
    }
    
    /**
     * Evaluates a team-form-only model on completed matches without changing live predictions.
     */
    @GetMapping("/team-form-backtest")
    public TeamFormBackTestReport runTeamFormBackTest(@RequestParam Instant from,
                                                       @RequestParam Instant to) {
        return teamFormBackTestService.run(from, to);
    }

    /**
     * Runs an explicit walk-forward evaluation without changing live predictions.
     * Dates must be ISO-8601 instants, for example 2026-01-01T00:00:00Z.
     */
    @GetMapping("/walk-forward-backtest")
    public BackTestReport runWalkForwardBackTest(@RequestParam Instant from,
                                                  @RequestParam Instant to) {
        return walkForwardBackTestService.run(from, to);
    }
}
