package com.mmo.module.fb.content.provenance;

import java.time.Instant;
import java.util.List;

/**
 * Traceability metadata for editorial claims. A claim is not considered verified
 * merely because it has a source label; verification must be performed by a
 * trusted ingestion/verification step or a human editor.
 */
public record ContentClaimProvenance(
        String claim,
        ClaimType type,
        VerificationStatus verificationStatus,
        List<SourceReference> sources,
        String limitation) {

    public ContentClaimProvenance {
        if (claim == null || claim.isBlank()) {
            throw new IllegalArgumentException("claim must not be blank");
        }
        type = type == null ? ClaimType.ANALYTICAL : type;
        verificationStatus = verificationStatus == null
                ? VerificationStatus.UNVERIFIED : verificationStatus;
        sources = sources == null ? List.of() : List.copyOf(sources);
        limitation = limitation == null ? "" : limitation;
        if (verificationStatus == VerificationStatus.VERIFIED && sources.isEmpty()) {
            throw new IllegalArgumentException("A verified claim must reference at least one source");
        }
    }

    public enum ClaimType { FACTUAL, STATISTICAL, ANALYTICAL, PREDICTIVE }
    public enum VerificationStatus { UNVERIFIED, SOURCE_ATTACHED, VERIFIED, REJECTED }

    public record SourceReference(
            String provider,
            String resource,
            String externalId,
            String field,
            Instant observedAt,
            String sourceUrl) {
        public SourceReference {
            if (provider == null || provider.isBlank()) {
                throw new IllegalArgumentException("source provider must not be blank");
            }
            if (resource == null || resource.isBlank()) {
                throw new IllegalArgumentException("source resource must not be blank");
            }
        }
    }
}
