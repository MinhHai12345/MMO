package com.mmo.module.fb.analytics.controller;

import com.mmo.module.fb.analytics.service.AnalyticsDataReadinessService;
import com.mmo.module.fb.analytics.service.AnalyticsDataReadinessService.DataReadinessReport;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only operational endpoint for inspecting analytics data coverage.
 */
@RestController
@RequestMapping("/api/fb/analytics")
@RequiredArgsConstructor
public class AnalyticsDataReadinessController {

    private final AnalyticsDataReadinessService analyticsDataReadinessService;

    @GetMapping("/data-readiness")
    public DataReadinessReport getDataReadiness() {
        return analyticsDataReadinessService.getReadiness();
    }
}
