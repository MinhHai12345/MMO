package com.mmo.module.fb.domain.match.entity;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.team.entity.Team;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Immutable-ish provider snapshot for one side of a match. The canonical
 * MatchAdvancedStats row remains the analytics-facing projection; this row preserves
 * the adapter payload representation and its provenance for audits/reconciliation.
 */
@Entity
@Table(name = "fb_match_statistics_source_snapshots",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_fb_match_stats_snapshot_source",
                columnNames = {"match_id", "team_id", "provider"}),
        indexes = {
                @Index(name = "idx_fb_match_stats_snapshot_provider",
                        columnList = "provider, fetched_at"),
                @Index(name = "idx_fb_match_stats_snapshot_match", columnList = "match_id")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchStatisticsSourceSnapshot extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MatchProvider provider;

    /** Serialized provider payload representation; retained for audit and future remapping. */
    @Lob
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "LONGTEXT")
    private String snapshotJson;

    @Column(name = "source_updated_at")
    private Instant sourceUpdatedAt;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    @PreUpdate
    private void validateSnapshot() {
        if (match == null || team == null || provider == null
                || snapshotJson == null || snapshotJson.isBlank()) {
            throw new IllegalStateException("Match statistics snapshot identity and payload are required");
        }
        if (fetchedAt == null) fetchedAt = Instant.now();
    }
}
