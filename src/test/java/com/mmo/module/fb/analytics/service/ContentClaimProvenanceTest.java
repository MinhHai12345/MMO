package com.mmo.module.fb.analytics.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ContentClaimProvenanceTest {

    @Test
    void acceptsCompleteTraceableClaim() {
        ContentClaimProvenance provenance = new ContentClaimProvenance(
                "Home team won four of its last five matches",
                "TEAM_FORM",
                "football-data:team-form:match-42",
                42L,
                "FOOTBALL_DATA",
                Instant.parse("2026-10-20T18:00:00Z"),
                Instant.parse("2026-10-21T08:00:00Z"),
                "Count wins across the five most recent completed fixtures",
                "One match was abandoned and excluded");

        assertTrue(provenance.isTraceable());
        assertTrue(provenance.validationErrors().isEmpty());
    }

    @Test
    void reportsMissingProvenanceFields() {
        ContentClaimProvenance provenance = new ContentClaimProvenance(
                "A factual claim", null, null, null, null, null, null, null, null);

        assertFalse(provenance.isTraceable());
        assertEquals(6, provenance.validationErrors().size());
    }

    @Test
    void doesNotTreatCaptureTimeAsObservationTime() {
        ContentClaimProvenance provenance = new ContentClaimProvenance(
                "A factual claim", "MATCH_STATS", "provider:match:42", 42L,
                "FOOTBALL_DATA", null, Instant.parse("2026-10-21T08:00:00Z"),
                "Provider match statistics", null);

        assertFalse(provenance.isTraceable());
        assertTrue(provenance.validationErrors().contains("Source observation timestamp is unknown."));
    }
}
