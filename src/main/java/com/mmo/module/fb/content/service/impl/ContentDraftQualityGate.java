package com.mmo.module.fb.content.service.impl;

import com.mmo.module.fb.content.service.ContentDraftService;
import com.mmo.module.fb.content.service.ContentDraftService.ContentDraft;
import com.mmo.module.fb.content.service.ContentDraftService.QualityReview;
import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService.MatchSocialInsight;
import com.mmo.module.fb.analytics.service.PreMatchAnalysisService.PreMatchReport;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Deterministic structural and editorial safety gate.
 *
 * This gate detects missing structure, placeholder evidence and prohibited
 * betting language. It does not prove factual accuracy; source verification
 * and human review remain mandatory.
 */
@Component
public class ContentDraftQualityGate {

    private static final List<String> BETTING_PHRASES = List.of(
            "guaranteed win", "sure bet", "risk-free bet", "lock of the day",
            "value bet", "betting pick", "chắc thắng", "kèo thơm", "bao thắng", "đánh kèo"
    );

    public ContentDraft evaluate(ContentDraft draft) {
        if (draft == null) {
            throw new IllegalArgumentException("Draft is required");
        }

        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        QualityReview existing = draft.qualityReview();
        if (existing != null) {
            blockers.addAll(existing.blockers());
            warnings.addAll(existing.warnings());
        }

        if (draft.matchId() == null || draft.matchId() <= 0) {
            blockers.add("A valid match identifier is required.");
        }
        if (draft.format() == null) {
            blockers.add("Draft format is missing.");
        }
        if (isBlank(draft.title())) {
            blockers.add("Draft title is missing.");
        }
        if (isBlank(draft.openingHook())) {
            blockers.add("Opening hook is missing.");
        }
        if (draft.outline().isEmpty()) {
            blockers.add("Draft outline is empty.");
        }
        if (draft.evidence().isEmpty()) {
            blockers.add("No evidence/source references were attached to the draft.");
            warnings.add("Claims cannot be traced to evidence until source references are added.");
        }
        if (draft.evidence().stream().anyMatch(ContentDraftQualityGate::isPlaceholderEvidence)) {
            blockers.add("Evidence contains a placeholder rather than traceable match data.");
        }
        if (draft.evidence().stream().anyMatch(ContentDraftQualityGate::isBlank)) {
            blockers.add("Evidence contains a blank source reference.");
        }

        String combinedText = Stream.of(
                        draft.title(), draft.openingHook(),
                        String.join(" ", draft.outline()),
                        String.join(" ", draft.shortHooks()),
                        String.join(" ", draft.evidence()),
                        String.join(" ", draft.caveats()))
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .reduce("", (left, right) -> left + " " + right);

        if (BETTING_PHRASES.stream().anyMatch(combinedText::contains)) {
            blockers.add("Draft contains prohibited betting or guaranteed-outcome language.");
        }
        if (draft.caveats().isEmpty()) {
            warnings.add("Draft has no explicit caveats; verify data limitations before publication.");
        }

        blockers = blockers.stream().distinct().toList();
        warnings = warnings.stream().distinct().toList();
        String decision = blockers.isEmpty() ? "NEEDS_EDITOR_REVIEW" : "BLOCKED";
        int score = Math.max(0, 100 - blockers.size() * 25 - warnings.size() * 5);
        QualityReview review = new QualityReview(decision, score, blockers, warnings);
        String status = blockers.isEmpty() ? draft.status() : "BLOCKED";

        return new ContentDraft(draft.matchId(), draft.format(), status, draft.title(),
                draft.openingHook(), draft.outline(), draft.shortHooks(), draft.evidence(),
                draft.caveats(), review);
    }


    /**
     * Adds analytics-specific evidence warnings to the single content-owned gate.
     * This is a deterministic editorial check, not a proof of factual accuracy.
     */
    public QualityReview review(ContentDraft draft, MatchSocialInsight insight) {
        return review(draft, insight, null, false);
    }

    /**
     * Reviews a draft against both social insight and the dedicated pre-match model report.
     * Missing or weak pre-match evidence is a warning requiring editorial review, not an
     * automatic blocker, because other verified analytics may still support useful content.
     */
    public QualityReview review(ContentDraft draft, MatchSocialInsight insight,
                                PreMatchReport preMatch, boolean preMatchExpected) {
        if (insight == null) {
            return new QualityReview("BLOCKED", 0,
                    List.of("Analytics insight is required to review this draft."), List.of());
        }

        ContentDraft structurallyReviewed = evaluate(draft);
        List<String> blockers = new ArrayList<>(structurallyReviewed.qualityReview().blockers());
        List<String> warnings = new ArrayList<>(structurallyReviewed.qualityReview().warnings());

        if (insight.home() == null || insight.home().name() == null
                || insight.away() == null || insight.away().name() == null) {
            blockers.add("Both teams must be identified before publication.");
        }
        warnings.addAll(insight.dataWarnings());
        if (preMatchExpected && preMatch == null) {
            warnings.add("Dedicated pre-match analysis is unavailable; do not publish model probabilities or expected-goal claims.");
        }
        if (preMatch != null) {
            warnings.addAll(preMatch.dataWarnings());
            if (!"GOOD".equalsIgnoreCase(preMatch.dataQuality())) {
                warnings.add("Pre-match analysis data quality is " + preMatch.dataQuality()
                        + "; explicitly disclose sample limitations in the final content.");
            }
            if (preMatch.goalModel() == null && preMatch.xgModel() == null) {
                warnings.add("Neither the goals model nor the xG model produced an estimate.");
            }
        }
        if (insight.prediction() == null) {
            warnings.add("No persisted prediction is available; do not imply a model forecast.");
        }
        if (insight.matchup() == null || insight.matchup().signals().isEmpty()) {
            warnings.add("No matchup signals are available; avoid strong tactical conclusions.");
        }
        if (insight.contentPlan() != null) {
            warnings.addAll(insight.contentPlan().factualCaveats());
        }
        if (insight.playerAnalytics() == null || insight.playerAnalytics().playersWithStats() == 0) {
            warnings.add("Player-level statistics are unavailable; omit player-specific performance claims.");
        }
        if (insight.postMatchAnalysis() == null
                && draft.format() == ContentDraftService.DraftFormat.POST_MATCH) {
            blockers.add("Post-match draft requested before a completed-match analysis is available.");
        }

        blockers = blockers.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
        warnings = warnings.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
        String decision = !blockers.isEmpty() ? "BLOCKED"
                : !warnings.isEmpty() ? "REVIEW_REQUIRED" : "READY_FOR_EDITOR_REVIEW";
        int score = Math.max(0, 100 - Math.min(60, blockers.size() * 30)
                - Math.min(35, warnings.size() * 5));
        return new QualityReview(decision, score, blockers, warnings);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isPlaceholderEvidence(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("no additional evidence was available")
                || normalized.contains("evidence unavailable")
                || normalized.contains("no data available");
    }
}
