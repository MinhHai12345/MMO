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
@Table(name = "fb_teams")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Team extends AbstractEntity {

    @Column(nullable = false)
    private String name;

    @Column
    private String shortName;

    @Column
    private String slug;

    @Column
    private String logoUrl;

    @Column
    private Integer ranking;

    @Column
    private String externalId;

    @Column(name = "founded_year")
    private Integer foundedYear;

    @Column(name = "stadium_name")
    private String stadiumName;

    @Column(name = "stadium_capacity")
    private Integer stadiumCapacity;

    @Column(name = "market_value")
    private BigDecimal marketValue;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TeamExternalId> externalIds = new ArrayList<>();

}