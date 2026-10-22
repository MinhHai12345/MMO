package com.mmo.module.fb.analytics.controller;

import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService;
import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService.MatchSocialInsight;
import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService.ContentCandidate;
import com.mmo.module.fb.analytics.service.ContentDraftService;
import com.mmo.module.fb.analytics.service.ContentDraftService.ContentDraft;
import com.mmo.module.fb.analytics.service.ContentDraftService.DraftFormat;
import com.mmo.module.fb.analytics.service.PlayerAnalyticsService;
import com.mmo.module.fb.analytics.service.PostMatchAnalysisService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.Instant;
import java.util.List;
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
    private final PlayerAnalyticsService playerAnalyticsService;
    private final PostMatchAnalysisService postMatchAnalysisService;
    private final ContentDraftService contentDraftService;


    @GetMapping("/social-insights")
    public List<ContentCandidate> discoverSocialInsights(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long leagueId,
            @RequestParam(defaultValue = "20") int limit) {
        return matchSocialAnalyticsService.discover(from, to, leagueId, limit);
    }

    @GetMapping("/{matchId}/player-analytics")
    public PlayerAnalyticsService.PlayerMatchInsight getPlayerAnalytics(@PathVariable Long matchId) {
        return playerAnalyticsService.analyzeMatch(matchId);
    }

    @GetMapping("/{matchId}/post-match-analysis")
    public PostMatchAnalysisService.PostMatchInsight getPostMatchAnalysis(@PathVariable Long matchId) {
        return postMatchAnalysisService.analyze(matchId);
    }

    @GetMapping("/{matchId}/content-draft")
    public ContentDraft getContentDraft(
            @PathVariable Long matchId,
            @RequestParam(defaultValue = "PRE_MATCH") DraftFormat format) {
        return contentDraftService.generate(matchId, format);
    }

    @GetMapping("/{matchId}/social-insight")
    public MatchSocialInsight getSocialInsight(@PathVariable Long matchId) {
        return matchSocialAnalyticsService.analyze(matchId);
    }
}
