package com.mmo.module.fb.analytics.service;

import java.time.Instant;

/**
 * Traceability metadata for one factual claim used in a generated content draft.
 *
 * <p>A provenance entry must identify the claim it supports and the source
 * from which the underlying observation was obtained. {@code observedAt} is
 * the source-data timestamp when known; {@code capturedAt} records when FB
 * ingested or recorded the evidence. These timestamps must not be substituted
 * for one another.</p>
 */
public record ContentClaimProvenance(
        String claim,
        String sourceType,
        String sourceRef,
        Long matchId,
        String provider,
        Instant observedAt,
        Instant capturedAt,
        String methodology,
        String limitations) {

    public ContentClaimProvenance {
        claim = normalize(claim);
        sourceType = normalize(sourceType);
        sourceRef = normalize(sourceRef);
        provider = normalize(provider);
        methodology = normalize(methodology);
        limitations = normalize(limitations);
    }

    /**
     * Returns validation errors rather than throwing, so the editorial gate can
     * report all provenance defects in one review.
     */
    public java.util.List<String> validationErrors() {
        java.util.List<String> errors = new java.util.ArrayList<>();
        if (claim == null) errors.add("Claim text is missing.");
        if (sourceType == null) errors.add("Claim source type is missing.");
        if (sourceRef == null) errors.add("Claim source reference is missing.");
        if (matchId == null || matchId <= 0) errors.add("Claim match ID is missing or invalid.");
        if (provider == null) errors.add("Claim source provider is missing.");
        if (capturedAt == null) errors.add("Evidence capture timestamp is missing.");
        if (observedAt == null) errors.add("Source observation timestamp is unknown.");
        return java.util.List.copyOf(errors);
    }

    public boolean isTraceable() {
        return validationErrors().isEmpty();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
