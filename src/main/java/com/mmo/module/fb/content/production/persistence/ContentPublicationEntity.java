package com.mmo.module.fb.content.production.persistence;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.content.persistence.ContentDraftRecordEntity;
import com.mmo.module.fb.content.production.PublicationPlatform;
import com.mmo.module.fb.content.production.PublicationStateMachine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "fb_content_publications",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_fb_content_publication_platform_idempotency",
                columnNames = {"platform", "idempotency_key"}),
        indexes = {
                @Index(name = "idx_fb_content_publication_state_schedule", columnList = "publication_state, scheduled_at"),
                @Index(name = "idx_fb_content_publication_draft", columnList = "draft_id, created_at_snapshot")
        })
@Getter
@Setter
@NoArgsConstructor
public class ContentPublicationEntity extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "draft_id", nullable = false)
    private ContentDraftRecordEntity draft;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PublicationPlatform platform;

    @Enumerated(EnumType.STRING)
    @Column(name = "publication_state", nullable = false, length = 24)
    private PublicationStateMachine.State state = PublicationStateMachine.State.DRAFT;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String body;

    @Column(name = "media_reference", length = 1000)
    private String mediaReference;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy;

    @Column(name = "approved_by", length = 120)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    @Column(name = "external_post_id", length = 255)
    private String externalPostId;

    @Column(name = "last_attempt_status", length = 32)
    private String lastAttemptStatus;

    @Column(name = "last_attempt_message", columnDefinition = "TEXT")
    private String lastAttemptMessage;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at_snapshot", nullable = false)
    private Instant createdAtSnapshot = Instant.now();

    @Column(name = "updated_at_snapshot", nullable = false)
    private Instant updatedAtSnapshot = Instant.now();
}
