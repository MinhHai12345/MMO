package com.mmo.module.fb.analytics.controller;

import com.mmo.module.fb.analytics.service.TeamTacticalAnalyticsService;
import com.mmo.module.fb.analytics.service.TeamTacticalAnalyticsService.TeamTacticalProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only historical tactical profile for one team. */
@RestController
@RequestMapping("/api/fb/analytics/teams")
@RequiredArgsConstructor
public class TeamTacticalAnalyticsController {

    private final TeamTacticalAnalyticsService teamTacticalAnalyticsService;

    @GetMapping("/{teamId}/tactical-profile")
    public TeamTacticalProfile getTacticalProfile(
            @PathVariable Long teamId,
            @RequestParam(required = false) Long leagueId,
            @RequestParam(defaultValue = "10") int historySize) {
        return teamTacticalAnalyticsService.analyze(teamId, leagueId, historySize);
    }
}
