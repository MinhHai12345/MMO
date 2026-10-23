package com.mmo.module.fb.content.provenance;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Canonical traceability model for claims used in editorial content.
 *
 * <p>Source observation time and FB capture time are distinct. A claim can be
 * traceable without being verified; verification is an explicit editorial or
 * trusted-source decision.</p>
 */
public record ContentClaimProvenance(
        String claim,
        Long matchId,
        ClaimType type,
        VerificationStatus verificationStatus,
        List<SourceReference> sources,
        Instant capturedAt,
        String methodology,
        String limitation) {

    public ContentClaimProvenance {
        if (claim == null || claim.isBlank()) {
            throw new IllegalArgumentException("claim must not be blank");
        }
        claim = claim.strip();
        type = type == null ? ClaimType.ANALYTICAL : type;
        verificationStatus = verificationStatus == null
                ? VerificationStatus.UNVERIFIED : verificationStatus;
        sources = sources == null ? List.of() : List.copyOf(sources);
        methodology = normalize(methodology);
        limitation = normalize(limitation);
        if (verificationStatus == VerificationStatus.VERIFIED && sources.isEmpty()) {
            throw new IllegalArgumentException("A verified claim must reference at least one source");
        }
    }

    /** Compatibility constructor for claims that have not yet been enriched with capture metadata. */
    public ContentClaimProvenance(
            String claim,
            ClaimType type,
            VerificationStatus verificationStatus,
            List<SourceReference> sources,
            String limitation) {
        this(claim, null, type, verificationStatus, sources, null, null, limitation);
    }

    /**
     * Returns validation errors for editorial review. These checks establish
     * traceability only; they do not establish that the claim is true.
     */
    public List<String> validationErrors() {
        List<String> errors = new ArrayList<>();
        if (matchId == null || matchId <= 0) {
            errors.add("Claim match ID is missing or invalid.");
        }
        if (sources.isEmpty()) {
            errors.add("Claim source reference is missing.");
        } else {
            for (SourceReference source : sources) {
                if (source.observedAt() == null) {
                    errors.add("Source observation timestamp is unknown.");
                }
            }
        }
        if (capturedAt == null) {
            errors.add("Evidence capture timestamp is missing.");
        }
        return errors.stream().distinct().toList();
    }

    public boolean isTraceable() {
        return validationErrors().isEmpty();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
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
            provider = provider.strip();
            resource = resource.strip();
            externalId = normalize(externalId);
            field = normalize(field);
            sourceUrl = normalize(sourceUrl);
        }
    }
}
