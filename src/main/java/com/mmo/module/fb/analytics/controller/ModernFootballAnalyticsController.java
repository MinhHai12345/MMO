package com.mmo.module.fb.analytics.controller;

import com.mmo.module.fb.analytics.service.ModernFootballAnalyticsService;
import com.mmo.module.fb.analytics.service.ModernFootballAnalyticsService.ModernMatchReport;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fb/analytics/matches")
@RequiredArgsConstructor
public class ModernFootballAnalyticsController {

    private final ModernFootballAnalyticsService analyticsService;

    @GetMapping("/{matchId}/modern-report")
    public ModernMatchReport getModernReport(@PathVariable Long matchId) {
        return analyticsService.analyze(matchId);
    }
}
