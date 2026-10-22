package com.mmo.module.fb.content.service.impl;

import com.mmo.module.fb.analytics.service.ContentDraftService.ContentDraft;
import com.mmo.module.fb.analytics.service.ContentDraftService.QualityReview;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic pre-review gate for generated football content.
 *
 * This gate validates that a draft has the minimum structure and explicit
 * evidence references. It does not claim to prove that arbitrary prose is
 * factually correct; source-level verification must be provided by the
 * generator and/or a human reviewer.
 */
@Component
public class ContentDraftQualityGate {

    private static final List<String> BETTING_PHRASES = List.of(
            "guaranteed win", "sure bet", "risk-free bet", "lock of the day",
            "chắc thắng", "kèo thơm", "bao thắng", "đánh kèo"
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
        }
        if (draft.evidence().stream().anyMatch(ContentDraftQualityGate::isPlaceholderEvidence)) {
            blockers.add("Evidence contains a placeholder rather than traceable match data.");
        }
        if (draft.evidence().stream().anyMatch(ContentDraftQualityGate::isBlank)) {
            blockers.add("Evidence contains a blank source reference.");
        }

        String combinedText = String.join(" ",
                safe(draft.title()), safe(draft.openingHook()), String.join(" ", draft.outline()),
                String.join(" ", draft.shortHooks())).toLowerCase(Locale.ROOT);
        if (BETTING_PHRASES.stream().anyMatch(combinedText::contains)) {
            blockers.add("Draft contains prohibited betting or guaranteed-outcome language.");
        }

        if (draft.caveats().isEmpty()) {
            warnings.add("Draft has no explicit caveats; verify data limitations before publication.");
        }
        if (draft.evidence().isEmpty()) {
            warnings.add("Claims cannot be traced to evidence until source references are added.");
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

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isPlaceholderEvidence(String value) {
        if (value == null) return false;
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("no additional evidence was available")
                || normalized.contains("evidence unavailable")
                || normalized.contains("no data available");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
