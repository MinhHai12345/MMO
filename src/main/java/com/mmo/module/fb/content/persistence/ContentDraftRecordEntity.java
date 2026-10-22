package com.mmo.module.fb.content.persistence;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.analytics.service.ContentDraftService.DraftFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "fb_content_drafts", uniqueConstraints = @UniqueConstraint(
        name = "uk_fb_content_draft_run_match_format", columnNames = {"workflow_run_id", "match_id", "draft_format"}))
@Getter
@Setter
@NoArgsConstructor
public class ContentDraftRecordEntity extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_run_id", nullable = false)
    private ContentWorkflowRunEntity workflowRun;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "draft_format", nullable = false, length = 24)
    private DraftFormat format;

    @Column(name = "generation_status", nullable = false, length = 40)
    private String generationStatus;

    @Column(name = "editorial_status", nullable = false, length = 32)
    private String editorialStatus = "PENDING_REVIEW";

    @Column(name = "quality_decision", length = 40)
    private String qualityDecision;

    @Column(name = "quality_score")
    private Integer qualityScore;

    @Column(length = 500)
    private String title;

    @Column(name = "opening_hook", columnDefinition = "TEXT")
    private String openingHook;

    @Column(name = "outline_json", columnDefinition = "LONGTEXT")
    private String outlineJson;

    @Column(name = "short_hooks_json", columnDefinition = "LONGTEXT")
    private String shortHooksJson;

    @Column(name = "evidence_json", columnDefinition = "LONGTEXT")
    private String evidenceJson;

    @Column(name = "caveats_json", columnDefinition = "LONGTEXT")
    private String caveatsJson;

    @Column(name = "blockers_json", columnDefinition = "LONGTEXT")
    private String blockersJson;

    @Column(name = "warnings_json", columnDefinition = "LONGTEXT")
    private String warningsJson;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    @Column(name = "version_number", nullable = false)
    private int versionNumber = 1;

    @Column(name = "created_at_snapshot", nullable = false)
    private Instant createdAtSnapshot = Instant.now();
}