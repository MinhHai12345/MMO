package com.mmo.module.fb.match.entity;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.team.entity.Team;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "fb_match_advanced_stats",
        uniqueConstraints = @UniqueConstraint(name = "uk_fb_advanced_stats_match_team", columnNames = {"match_id", "team_id"}),
        indexes = @Index(name = "idx_fb_advanced_stats_team", columnList = "team_id"))
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MatchAdvancedStats extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(precision = 8, scale = 3)
    private BigDecimal ppda;

    @Column(name = "ppda_att")
    private Integer ppdaAtt;

    @Column(name = "ppda_def")
    private Integer ppdaDef;

    @Column(name = "field_tilt", precision = 5, scale = 2)
    private BigDecimal fieldTilt;

    @Column(name = "xg_chain", precision = 7, scale = 3)
    private BigDecimal xgChain;

    @Column(name = "xg_buildup", precision = 7, scale = 3)
    private BigDecimal xgBuildup;

    @Column(precision = 5, scale = 2)
    private BigDecimal possession;

    @Column(name = "deep_completions")
    private Integer deepCompletions;

    @Column(name = "final_third_entries")
    private Integer finalThirdEntries;

    @Column(name = "shots_from_box")
    private Integer shotsFromBox;

    @Column(name = "shots_outside_box")
    private Integer shotsOutsideBox;

    @Column(name = "set_piece_xg", precision = 7, scale = 3)
    private BigDecimal setPieceXg;

    @Column(name = "counter_attack_xg", precision = 7, scale = 3)
    private BigDecimal counterAttackXg;

    @Column(name = "total_shots")
    private Integer totalShots;

    @Column(name = "shots_on_target")
    private Integer shotsOnTarget;

    @Column(name = "shots_off_target")
    private Integer shotsOffTarget;

    @Column(name = "blocked_shots")
    private Integer blockedShots;

    @Column(name = "corner_kicks")
    private Integer cornerKicks;

    @Column(name = "fouls")
    private Integer fouls;

    @Column(name = "yellow_cards")
    private Integer yellowCards;

    @Column(name = "red_cards")
    private Integer redCards;

    @Column(name = "offsides")
    private Integer offsides;

    @Column(name = "total_passes")
    private Integer totalPasses;

    @Column(name = "accurate_passes")
    private Integer accuratePasses;

    @Column(name = "pass_accuracy", precision = 5, scale = 2)
    private BigDecimal passAccuracy;

    @Column(name = "goalkeeper_saves")
    private Integer goalkeeperSaves;

    @Column(name = "big_chances_created")
    private Integer bigChancesCreated;

    @Column(name = "big_chances_missed")
    private Integer bigChancesMissed;

    @Column(name = "statistics_provider", length = 32)
    private String statisticsProvider;

    @Column(name = "statistics_synced_at")
    private java.time.Instant statisticsSyncedAt;
}
