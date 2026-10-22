package com.mmo.module.fb.content.persistence;

import com.mmo.core.entity.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "fb_content_workflow_runs")
@Getter
@Setter
@NoArgsConstructor
public class ContentWorkflowRunEntity extends AbstractEntity {

    @Column(name = "workflow_status", nullable = false, length = 40)
    private String status;

    @Enumerated(EnumType.STRING)
    @Column(name = "draft_format", nullable = false, length = 24)
    private com.mmo.module.fb.analytics.service.ContentDraftService.DraftFormat format;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "filter_from")
    private Instant filterFrom;

    @Column(name = "filter_to")
    private Instant filterTo;

    @Column(name = "league_id")
    private Long leagueId;

    @Column(name = "requested_limit", nullable = false)
    private int requestedLimit;

    @Column(name = "candidates_discovered", nullable = false)
    private int candidatesDiscovered;

    @Column(name = "drafts_generated", nullable = false)
    private int draftsGenerated;

    @Column(name = "drafts_needing_review", nullable = false)
    private int draftsNeedingReview;

    @Column(name = "drafts_blocked", nullable = false)
    private int draftsBlocked;

    @Column(name = "failures", nullable = false)
    private int failures;

    @Lob
    @Column(name = "warnings_json", columnDefinition = "LONGTEXT")
    private String warningsJson;
}