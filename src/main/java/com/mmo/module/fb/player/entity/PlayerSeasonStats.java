package com.mmo.module.fb.player.entity;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.league.entity.Season;
import com.mmo.module.fb.team.entity.Team;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Season-scoped player aggregates; never overwrite these values on the global player profile. */
@Entity
@Table(name = "fb_player_season_stats",
        uniqueConstraints = @UniqueConstraint(name = "uk_fb_player_season_provider",
                columnNames = {"player_id", "season_id", "provider"}),
        indexes = {
                @Index(name = "idx_fb_player_season_stats_season", columnList = "season_id"),
                @Index(name = "idx_fb_player_season_stats_team", columnList = "team_id")
        })
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PlayerSeasonStats extends AbstractEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Provider provider;

    private Integer appearances;
    @Column(name = "minutes_played")
    private Integer minutesPlayed;
    private Integer goals;
    @Column(precision = 12, scale = 4)
    private BigDecimal xg;
    private Integer assists;
    @Column(precision = 12, scale = 4)
    private BigDecimal xa;
    private Integer shots;
    @Column(name = "key_passes")
    private Integer keyPasses;
    @Column(name = "yellow_cards")
    private Integer yellowCards;
    @Column(name = "red_cards")
    private Integer redCards;
    @Column(name = "non_penalty_goals")
    private Integer nonPenaltyGoals;
    @Column(name = "npxg", precision = 12, scale = 4)
    private BigDecimal npxg;
    @Column(name = "xg_chain", precision = 12, scale = 4)
    private BigDecimal xgChain;
    @Column(name = "xg_buildup", precision = 12, scale = 4)
    private BigDecimal xgBuildup;
}
