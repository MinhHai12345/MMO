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
       uniqueConstraints = @UniqueConstraint(columnNames = {"player_id", "match_id"}))
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

    private Integer goals = 0;

    private Integer assists = 0;

    private Integer shots = 0;

    @Column(name = "shots_on_target")
    private Integer shotsOnTarget = 0;

    private BigDecimal xg = BigDecimal.ZERO;

    private BigDecimal xa = BigDecimal.ZERO;

    private Integer passes = 0;

    @Column(name = "pass_accuracy")
    private BigDecimal passAccuracy;

    @Column(name = "key_passes")
    private Integer keyPasses = 0;

    private Integer tackles = 0;

    private Integer interceptions = 0;

    @Column(name = "duels_won")
    private Integer duelsWon = 0;

    private Integer dribbles = 0;

    @Column(name = "fouls_drawn")
    private Integer foulsDrawn = 0;

    @Column(name = "fouls_committed")
    private Integer foulsCommitted = 0;

    @Column(name = "yellow_cards")
    private Integer yellowCards = 0;

    @Column(name = "red_cards")
    private Integer redCards = 0;
}
