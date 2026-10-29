package com.mmo.module.fb.league.entity;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.ingest.enums.Provider;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fb_league_external_ids",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_fb_league_provider_entity", columnNames = {"league_id", "provider"}),
                @UniqueConstraint(name = "uk_fb_league_provider_external", columnNames = {"provider", "external_id"})
        },
        indexes = @Index(name = "idx_league_external_ids_provider", columnList = "provider"))
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LeagueExternalId extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private Provider provider;

    /** Stable provider-side ID used to resolve the mapping row. */
    @Column(name = "external_id", nullable = false, length = 128)
    private String externalId;

    /** Provider-specific value used in fetch requests (code, name, slug, or numeric ID). */
    @Column(name = "identify", length = 128)
    private String identify;
}
