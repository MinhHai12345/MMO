package com.mmo.module.fb.content.provenance;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContentClaimProvenanceTest {

    @Test
    void defaultsMissingVerificationToUnverified() {
        var claim = new ContentClaimProvenance("Team scored 2 goals", null, null, null, null);
        assertEquals(ContentClaimProvenance.VerificationStatus.UNVERIFIED, claim.verificationStatus());
        assertTrue(claim.sources().isEmpty());
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
                "FOOTBALL_DATA", "MATCH", "fixture-123", "score.fullTime.home", null, null);
        var claim = new ContentClaimProvenance(
                "Home team scored 2 goals",
                ContentClaimProvenance.ClaimType.STATISTICAL,
                ContentClaimProvenance.VerificationStatus.VERIFIED,
                List.of(source),
                "");
        assertEquals(1, claim.sources().size());
    }
}
