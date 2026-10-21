package com.mmo.module.fb.domain.entity;

import com.mmo.core.entity.AbstractEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "fb_leagues")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class League extends AbstractEntity {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, unique = true, length = 320)
    private String slug;

    @Column(length = 100)
    private String country;

    @Column(name = "logo_url", length = 2048)
    private String logoUrl;

    @Column(name = "popularity_score", precision = 8, scale = 3, nullable = false)
    @Builder.Default
    private BigDecimal popularityScore = BigDecimal.ZERO;

    @Column(name = "betting_market", length = 100)
    private String bettingMarket;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @OneToMany(mappedBy = "league", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LeagueExternalId> externalIds = new ArrayList<>();
}
