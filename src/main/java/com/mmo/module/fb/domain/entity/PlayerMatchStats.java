package com.mmo.module.fb.domain.entity;

import com.mmo.core.entity.AbstractEntity;
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

    @Builder.Default
    private BigDecimal xg = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal xa = BigDecimal.ZERO;

    @Builder.Default
    private Integer passes = 0;

    @Column(name = "pass_accuracy")
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
