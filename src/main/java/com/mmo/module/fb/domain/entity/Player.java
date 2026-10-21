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
@Table(name = "fb_players",
        indexes = {
                @Index(name = "idx_fb_player_team", columnList = "team_id"),
                @Index(name = "idx_fb_player_name", columnList = "name"),
                @Index(name = "idx_fb_player_active", columnList = "is_active")
        })
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Player extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 40)
    private String position;

    private Integer age;

    @Column(length = 100)
    private String nationality;

    private Integer height;

    private Integer weight;

    @Column(name = "market_value", precision = 16, scale = 2)
    private BigDecimal marketValue;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @OneToMany(mappedBy = "player", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PlayerMatchStats> matchStats = new ArrayList<>();

    @OneToMany(mappedBy = "player", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PlayerExternalId> externalIds = new ArrayList<>();
}
