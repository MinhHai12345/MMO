package com.mmo.module.fb.domain.match.entity;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Provenance for one canonical field after cross-provider merge. */
@Entity
@Table(name = "fb_match_field_provenance",
        uniqueConstraints = @UniqueConstraint(name = "uk_fb_match_field_provenance",
                columnNames = {"match_id", "field_key"}),
        indexes = @Index(name = "idx_fb_match_field_source", columnList = "provider, dataset_key"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchFieldProvenance extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Column(name = "field_key", nullable = false, length = 80)
    private String fieldKey;

    @Column(name = "dataset_key", nullable = false, length = 40)
    private String datasetKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MatchProvider provider;

    @Column(name = "source_updated_at")
    private Instant sourceUpdatedAt;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    @PreUpdate
    private void validate() {
        if (match == null || fieldKey == null || fieldKey.isBlank()
                || datasetKey == null || datasetKey.isBlank() || provider == null) {
            throw new IllegalStateException("Match field provenance identity is required");
        }
        if (fetchedAt == null) fetchedAt = Instant.now();
    }
}
