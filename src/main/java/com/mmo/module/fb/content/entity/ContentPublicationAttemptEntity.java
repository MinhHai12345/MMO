package com.mmo.module.fb.content.entity;

import com.mmo.core.entity.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "fb_content_publication_attempts",
        indexes = @Index(name = "idx_fb_publication_attempt_publication", columnList = "publication_id, attempt_number"))
@Getter
@Setter
@NoArgsConstructor
public class ContentPublicationAttemptEntity extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "publication_id", nullable = false)
    private ContentPublicationEntity publication;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "execution_mode", nullable = false, length = 24)
    private String executionMode;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "request_summary", columnDefinition = "TEXT")
    private String requestSummary;

    @Column(name = "response_summary", columnDefinition = "TEXT")
    private String responseSummary;

    @Column(name = "requested_by", length = 120)
    private String requestedBy;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
