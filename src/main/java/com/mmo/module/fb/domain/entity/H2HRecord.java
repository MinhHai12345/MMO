package com.mmo.module.fb.domain.entity;

import com.mmo.core.entity.AbstractEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "fb_h2h_records",
        uniqueConstraints = @UniqueConstraint(name = "uk_fb_h2h_home_away", columnNames = {"home_team_id", "away_team_id"}),
        indexes = @Index(name = "idx_fb_h2h_away_home", columnList = "away_team_id, home_team_id"))
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class H2HRecord extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_team_id", nullable = false)
    private Team homeTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "away_team_id", nullable = false)
    private Team awayTeam;

    @Column(name = "total_matches")
    @Builder.Default
    private Integer totalMatches = 0;

    @Column(name = "home_wins")
    @Builder.Default
    private Integer homeWins = 0;

    @Column(name = "away_wins")
    @Builder.Default
    private Integer awayWins = 0;

    @Builder.Default
    private Integer draws = 0;

    @Column(name = "home_goals")
    @Builder.Default
    private Integer homeGoals = 0;

    @Column(name = "away_goals")
    @Builder.Default
    private Integer awayGoals = 0;

    @Column(name = "last_updated")
    private Instant lastUpdated;
}
