package com.mmo.module.fb.content.provenance;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContentClaimProvenanceTest {

    @Test
    void defaultsMissingVerificationToUnverified() {
        var claim = new ContentClaimProvenance("Team scored 2 goals", null, null, null, "");
        assertEquals(ContentClaimProvenance.VerificationStatus.UNVERIFIED, claim.verificationStatus());
        assertTrue(claim.sources().isEmpty());
        assertFalse(claim.isTraceable());
    }

    @Test
    void rejectsVerifiedClaimWithoutSource() {
        assertThrows(IllegalArgumentException.class, () -> new ContentClaimProvenance(
                "The team scored 2 goals",
                ContentClaimProvenance.ClaimType.STATISTICAL,
                ContentClaimProvenance.VerificationStatus.VERIFIED,
                List.of(),
                ""));
    }

    @Test
    void verifiedClaimRequiresAndAcceptsSource() {
        var source = new ContentClaimProvenance.SourceReference(
                "FOOTBALL_DATA", "MATCH", "fixture-123", "score.fullTime.home",
                Instant.parse("2026-10-20T18:00:00Z"), null);
        var claim = new ContentClaimProvenance(
                "Home team scored 2 goals",
                42L,
                ContentClaimProvenance.ClaimType.STATISTICAL,
                ContentClaimProvenance.VerificationStatus.VERIFIED,
                List.of(source),
                Instant.parse("2026-10-21T08:00:00Z"),
                "Read the provider's full-time score",
                "");
        assertEquals(1, claim.sources().size());
        assertTrue(claim.isTraceable());
    }

    @Test
    void doesNotSubstituteCaptureTimeForObservationTime() {
        var source = new ContentClaimProvenance.SourceReference(
                "FOOTBALL_DATA", "MATCH", "fixture-123", "score.fullTime.home", null, null);
        var claim = new ContentClaimProvenance(
                "Home team scored 2 goals",
                42L,
                ContentClaimProvenance.ClaimType.STATISTICAL,
                ContentClaimProvenance.VerificationStatus.SOURCE_ATTACHED,
                List.of(source),
                Instant.parse("2026-10-21T08:00:00Z"),
                null,
                "Provider did not expose source observation time");
        assertFalse(claim.isTraceable());
        assertTrue(claim.validationErrors().contains("Source observation timestamp is unknown."));
    }

    @Test
    void reportsMissingMatchSourceAndCaptureMetadata() {
        var claim = new ContentClaimProvenance(
                "A factual claim",
                null,
                ContentClaimProvenance.ClaimType.FACTUAL,
                ContentClaimProvenance.VerificationStatus.UNVERIFIED,
                List.of(),
                null,
                null,
                null);
        assertEquals(3, claim.validationErrors().size());
        assertTrue(claim.validationErrors().contains("Claim match ID is missing or invalid."));
        assertTrue(claim.validationErrors().contains("Claim source reference is missing."));
        assertTrue(claim.validationErrors().contains("Evidence capture timestamp is missing."));
    }
}
