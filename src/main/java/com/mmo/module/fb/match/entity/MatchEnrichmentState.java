package com.mmo.module.fb.match.entity;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.match.enums.MatchEnrichmentResource;
import com.mmo.module.fb.match.enums.MatchEnrichmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Provider/resource-specific ingestion state. Kept separate from Match so one provider's
 * retries never throttle another provider or another enrichment resource.
 */
@Entity
@Table(name = "fb_match_enrichment_states",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_fb_match_enrichment_provider_resource",
                columnNames = {"match_id", "provider", "resource"}),
        indexes = {
                @Index(name = "idx_fb_match_enrichment_retry",
                        columnList = "provider, resource, status, next_retry_at"),
                @Index(name = "idx_fb_match_enrichment_match", columnList = "match_id")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchEnrichmentState extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Provider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MatchEnrichmentResource resource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    @Builder.Default
    private MatchEnrichmentStatus status = MatchEnrichmentStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Version
    @Column(name = "version")
    private Long version;

    public void markAttempt(Instant attemptedAt) {
        this.lastAttemptAt = attemptedAt;
        this.attemptCount++;
        this.status = MatchEnrichmentStatus.IN_PROGRESS;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void markSucceeded(Instant completedAt) {
        this.status = MatchEnrichmentStatus.SUCCEEDED;
        this.lastSuccessAt = completedAt;
        this.nextRetryAt = null;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void markEmpty(Instant completedAt, Instant retryAt) {
        this.status = MatchEnrichmentStatus.EMPTY;
        this.lastSuccessAt = completedAt;
        this.nextRetryAt = retryAt;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void markFailed(String code, String message, Instant retryAt) {
        this.status = MatchEnrichmentStatus.FAILED;
        this.errorCode = code;
        this.errorMessage = message == null ? null
                : message.substring(0, Math.min(message.length(), 1000));
        this.nextRetryAt = retryAt;
    }
}
