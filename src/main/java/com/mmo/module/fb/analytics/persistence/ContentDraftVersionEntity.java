package com.mmo.module.fb.analytics.persistence;

import com.mmo.core.entity.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "fb_content_draft_versions")
@Getter
@Setter
@NoArgsConstructor
public class ContentDraftVersionEntity extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "draft_id", nullable = false)
    private ContentDraftRecordEntity draft;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "change_type", nullable = false, length = 32)
    private String changeType;

    @Column(name = "editorial_status", nullable = false, length = 32)
    private String editorialStatus;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "LONGTEXT")
    private String snapshotJson;

    @Column(name = "changed_by", length = 120)
    private String changedBy;

    @Column(name = "change_notes", columnDefinition = "TEXT")
    private String changeNotes;

    @Column(name = "created_at_snapshot", nullable = false)
    private Instant createdAt = Instant.now();
}