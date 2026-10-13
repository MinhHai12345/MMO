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
@Table(name = "fb_players")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Player extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(nullable = false)
    private String name;

    private String position;

    private Integer age;

    private String nationality;

    private Integer height;

    private Integer weight;

    @Column(name = "market_value")
    private BigDecimal marketValue;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @OneToMany(mappedBy = "player", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlayerMatchStats> matchStats = new ArrayList<>();

    @OneToMany(mappedBy = "player", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlayerExternalId> externalIds = new ArrayList<>();
}
