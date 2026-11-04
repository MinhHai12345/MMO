package com.mmo.module.fb.match.entity;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.ingest.enums.Provider;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "fb_match_external_ids",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_fb_match_provider_entity", columnNames = {"match_id", "provider"}),
                @UniqueConstraint(name = "uk_fb_match_provider_external", columnNames = {"provider", "external_id"})
        },
        indexes = @Index(name = "idx_match_external_ids_provider", columnList = "provider"))
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MatchExternalId extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private Provider provider;

    @Column(name = "external_id", nullable = false, length = 128)
    private String externalId;

    @PrePersist
    @PreUpdate
    private void normalizeIdentity() {
        if (externalId != null) {
            externalId = externalId.trim();
        }
        if (externalId == null || externalId.isEmpty()) {
            throw new IllegalStateException("Match external ID must not be blank");
        }
        if (provider == null) {
            throw new IllegalStateException("Match external ID provider is required");
        }
        if (match == null) {
            throw new IllegalStateException("Match external ID must reference a canonical match");
        }
    }
}
