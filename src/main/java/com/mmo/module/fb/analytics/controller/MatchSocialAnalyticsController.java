package com.mmo.module.fb.analytics.controller;

import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService;
import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService.MatchSocialInsight;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only analytics payload for football social-content generation.
 */
@RestController
@RequestMapping("/api/fb/analytics/matches")
@RequiredArgsConstructor
public class MatchSocialAnalyticsController {

    private final MatchSocialAnalyticsService matchSocialAnalyticsService;

    @GetMapping("/{matchId}/social-insight")
    public MatchSocialInsight getSocialInsight(@PathVariable Long matchId) {
        return matchSocialAnalyticsService.analyze(matchId);
    }
}
