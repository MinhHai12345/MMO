package com.mmo.module.fb.team.entity;

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
@Table(name = "fb_teams",
        indexes = {
                @Index(name = "idx_fb_team_name", columnList = "name"),
                @Index(name = "idx_fb_team_slug", columnList = "slug")
        })
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Team extends AbstractEntity {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "short_name", length = 80)
    private String shortName;

    @Column(length = 320)
    private String slug;

    @Column(name = "logo_url", length = 2048)
    private String logoUrl;

    private Integer ranking;

    @Column(name = "founded_year")
    private Integer foundedYear;

    @Column(name = "stadium_name", length = 180)
    private String stadiumName;

    @Column(name = "stadium_capacity")
    private Integer stadiumCapacity;

    @Column(name = "market_value", precision = 16, scale = 2)
    private BigDecimal marketValue;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TeamExternalId> externalIds = new ArrayList<>();
}
