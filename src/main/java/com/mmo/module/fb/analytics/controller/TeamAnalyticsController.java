package com.mmo.module.fb.analytics.controller;

import com.mmo.module.fb.analytics.service.TeamAnalyticsService;
import com.mmo.module.fb.analytics.service.TeamAnalyticsService.TeamPerformance;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only team analytics endpoints backed by persisted core fixtures and results. */
@RestController
@RequestMapping("/api/fb/analytics/teams")
@RequiredArgsConstructor
public class TeamAnalyticsController {

    private final TeamAnalyticsService teamAnalyticsService;

    @GetMapping("/{teamId}/performance")
    public TeamPerformance getTeamPerformance(
            @PathVariable Long teamId,
            @RequestParam(defaultValue = "10") int historySize) {
        return teamAnalyticsService.analyze(teamId, historySize);
    }
}
