package com.mmo.module.fb.content.service.impl;

import com.mmo.module.fb.analytics.service.ContentDraftService.ContentDraft;
import com.mmo.module.fb.analytics.service.ContentDraftService.DraftFormat;
import com.mmo.module.fb.analytics.service.ContentDraftService.QualityReview;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContentDraftQualityGateTest {

    private final ContentDraftQualityGate gate = new ContentDraftQualityGate();

    @Test
    void blocksDraftWithoutEvidence() {
        ContentDraft input = new ContentDraft(10L, DraftFormat.PRE_MATCH, "READY_FOR_EDITOR_REVIEW",
                "Match preview", "Opening", List.of("Recent form"), List.of(),
                List.of(), List.of("Lineup may change"), null);

        ContentDraft result = gate.evaluate(input);

        assertEquals("BLOCKED", result.status());
        assertEquals("BLOCKED", result.qualityReview().decision());
        assertTrue(result.qualityReview().blockers().stream()
                .anyMatch(reason -> reason.contains("evidence/source references")));
    }

    @Test
    void blocksBettingAndGuaranteedOutcomeLanguage() {
        ContentDraft input = new ContentDraft(10L, DraftFormat.PRE_MATCH, "READY_FOR_EDITOR_REVIEW",
                "Guaranteed win", "Opening", List.of("Recent form"), List.of(),
                List.of("match:10:form"), List.of("Sample is limited"),
                new QualityReview("PASS", 100, List.of(), List.of()));

        ContentDraft result = gate.evaluate(input);

        assertEquals("BLOCKED", result.status());
        assertTrue(result.qualityReview().blockers().stream()
                .anyMatch(reason -> reason.contains("betting or guaranteed-outcome")));
    }

    @Test
    void allowsStructurallyValidDraftButRequiresEditorReview() {
        ContentDraft input = new ContentDraft(10L, DraftFormat.PRE_MATCH, "READY_FOR_EDITOR_REVIEW",
                "Match preview", "Opening", List.of("Recent form"), List.of("What to watch"),
                List.of("match:10:recent-form"), List.of("Small sample"), null);

        ContentDraft result = gate.evaluate(input);

        assertEquals("READY_FOR_EDITOR_REVIEW", result.status());
        assertEquals("NEEDS_EDITOR_REVIEW", result.qualityReview().decision());
        assertTrue(result.qualityReview().blockers().isEmpty());
    }

    @Test
    void preservesExistingBlockersAndWarnings() {
        ContentDraft input = new ContentDraft(10L, DraftFormat.PRE_MATCH, "BLOCKED",
                "Match preview", "Opening", List.of("Recent form"), List.of(),
                List.of("match:10:form"), List.of("Caveat"),
                new QualityReview("BLOCKED", 20, List.of("Existing blocker"), List.of("Existing warning")));

        ContentDraft result = gate.evaluate(input);

        assertTrue(result.qualityReview().blockers().contains("Existing blocker"));
        assertTrue(result.qualityReview().warnings().contains("Existing warning"));
    }
}
