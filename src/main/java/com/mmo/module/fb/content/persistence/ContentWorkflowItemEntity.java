package com.mmo.module.fb.content.persistence;

import com.mmo.core.entity.AbstractEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "fb_content_workflow_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_fb_content_workflow_item_match",
                columnNames = {"workflow_run_id", "match_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ContentWorkflowItemEntity extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_run_id", nullable = false)
    private ContentWorkflowRunEntity workflowRun;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "item_status", nullable = false, length = 40)
    private String status = "PENDING";

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "draft_id")
    private Long draftId;
}
