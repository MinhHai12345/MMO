package com.mmo.module.fb.analytics.service.impl;

import com.mmo.module.fb.analytics.service.ContentDraftService.ContentDraft;
import com.mmo.module.fb.analytics.service.ContentDraftService.QualityReview;
import com.mmo.module.fb.analytics.service.EditorialQualityGateService;
import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService.MatchSocialInsight;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class EditorialQualityGateServiceImpl implements EditorialQualityGateService {

    private static final List<String> PROHIBITED_BETTING_TERMS = List.of(
            "betting", "bet on", "wager", "stake", "gamble", "sure win", "lock of the day",
            "cược", "nhà cái", "kèo", "đặt tiền", "ăn chắc");

    @Override
    public QualityReview review(ContentDraft draft, MatchSocialInsight insight) {
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int score = 100;

        if (draft.title() == null || draft.title().isBlank()) {
            blockers.add("Draft title is missing.");
        }
        if (draft.openingHook() == null || draft.openingHook().isBlank()) {
            blockers.add("Opening hook is missing.");
        }
        if (draft.outline().isEmpty()) {
            blockers.add("Draft outline is empty.");
        }

        String combined = String.join(" ", draft.title(), draft.openingHook(),
                String.join(" ", draft.outline()), String.join(" ", draft.shortHooks()))
                .toLowerCase(Locale.ROOT);
        if (PROHIBITED_BETTING_TERMS.stream().anyMatch(combined::contains)) {
            blockers.add("Betting or gambling-oriented language is not allowed in football editorial content.");
        }

        if (insight.home() == null || insight.home().name() == null
                || insight.away() == null || insight.away().name() == null) {
            blockers.add("Both teams must be identified before publication.");
        }
        if (!insight.dataWarnings().isEmpty()) {
            warnings.addAll(insight.dataWarnings());
        }
        if (insight.prediction() == null) {
            warnings.add("No persisted prediction is available; do not imply a model forecast.");
        }
        if (insight.matchup() == null || insight.matchup().signals().isEmpty()) {
            warnings.add("No matchup signals are available; avoid strong tactical conclusions.");
        }
        if (insight.contentPlan() != null && !insight.contentPlan().factualCaveats().isEmpty()) {
            warnings.addAll(insight.contentPlan().factualCaveats());
        }
        if (insight.postMatchAnalysis() == null && "POST_MATCH".equals(draft.format().name())) {
            blockers.add("Post-match draft requested before a completed-match analysis is available.");
        }
        if (insight.playerAnalytics() == null
                || insight.playerAnalytics().playersWithStats() == 0) {
            warnings.add("Player-level statistics are unavailable; omit player-specific performance claims.");
        }

        score -= Math.min(60, blockers.size() * 30);
        score -= Math.min(35, warnings.size() * 5);
        score = Math.max(0, score);
        String decision = !blockers.isEmpty() ? "BLOCKED"
                : !warnings.isEmpty() ? "REVIEW_REQUIRED" : "READY_FOR_EDITOR_REVIEW";
        return new QualityReview(decision, score, blockers, warnings.stream().distinct().toList());
    }
}
