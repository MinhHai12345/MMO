package com.mmo.module.fb.domain.entity;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.domain.enums.Provider;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fb_match_external_ids",
       uniqueConstraints = {
               @UniqueConstraint(name = "uk_fb_match_provider_entity", columnNames = {"match_id", "provider"}),
               @UniqueConstraint(name = "uk_fb_match_provider_external", columnNames = {"provider", "external_id"})
       })
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MatchExternalId extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;

    @Column(name = "external_id", nullable = false)
    private String externalId;
}
