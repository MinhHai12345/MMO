package com.mmo.module.fb.league.entity;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.domain.enums.Provider;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fb_season_external_ids",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_fb_season_provider_entity", columnNames = {"season_id", "provider"}),
                @UniqueConstraint(name = "uk_fb_season_provider_external", columnNames = {"provider", "external_id"})
        },
        indexes = @Index(name = "idx_season_external_ids_provider", columnList = "provider"))
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeasonExternalId extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private Provider provider;

    @Column(name = "external_id", nullable = false, length = 128)
    private String externalId;
}
