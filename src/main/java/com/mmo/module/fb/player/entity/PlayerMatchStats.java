package com.mmo.module.fb.player.entity;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.team.entity.Team;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "fb_player_match_stats",
        uniqueConstraints = @UniqueConstraint(name = "uk_fb_player_match_stats_player_match", columnNames = {"player_id", "match_id"}),
        indexes = @Index(name = "idx_fb_player_match_stats_match", columnList = "match_id"))
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PlayerMatchStats extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    /** Team represented by this player in this fixture; distinct from the player's current team. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(name = "minutes_played")
    private Integer minutesPlayed;

    @Builder.Default
    private Integer goals = 0;

    @Builder.Default
    private Integer assists = 0;

    @Builder.Default
    private Integer shots = 0;

    @Column(name = "shots_on_target")
    @Builder.Default
    private Integer shotsOnTarget = 0;

    /** Null means the provider did not supply xG; zero is a valid observed value. */
    @Column(precision = 7, scale = 3)
    private BigDecimal xg;

    /** Null means the provider did not supply xA; zero is a valid observed value. */
    @Column(precision = 7, scale = 3)
    private BigDecimal xa;

    @Builder.Default
    private Integer passes = 0;

    @Column(name = "pass_accuracy", precision = 5, scale = 2)
    private BigDecimal passAccuracy;

    @Column(name = "key_passes")
    @Builder.Default
    private Integer keyPasses = 0;

    @Builder.Default
    private Integer tackles = 0;

    @Builder.Default
    private Integer interceptions = 0;

    @Column(name = "duels_won")
    @Builder.Default
    private Integer duelsWon = 0;

    @Builder.Default
    private Integer dribbles = 0;

    @Column(name = "fouls_drawn")
    @Builder.Default
    private Integer foulsDrawn = 0;

    @Column(name = "fouls_committed")
    @Builder.Default
    private Integer foulsCommitted = 0;

    @Column(name = "yellow_cards")
    @Builder.Default
    private Integer yellowCards = 0;

    @Column(name = "red_cards")
    @Builder.Default
    private Integer redCards = 0;
}
