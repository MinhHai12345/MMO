package com.mmo.module.fb.match.entity;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.league.entity.Season;
import com.mmo.module.fb.team.entity.Team;
import com.mmo.module.fb.match.enums.MatchStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "fb_matches",
        indexes = {
                @Index(name = "idx_match_time", columnList = "match_time"),
                @Index(name = "idx_match_status_time", columnList = "status, match_time"),
                @Index(name = "idx_match_season_teams_time",
                        columnList = "season_id, home_team_id, away_team_id, match_time")
        })
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Match extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_team_id", nullable = false)
    private Team homeTeam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "away_team_id", nullable = false)
    private Team awayTeam;

    @Column(name = "match_time", nullable = false)
    private Instant matchTime;

    @Column(name = "last_odds_synced_at")
    private Instant lastOddsSyncedAt;

    @Column(name = "statistics_last_attempt_at")
    private Instant statisticsLastAttemptAt;

    @Column(name = "round_info", length = 160)
    private String roundInfo;

    @Column(length = 180)
    private String venue;

    private Integer attendance;

    @Column(length = 160)
    private String referee;

    @Column(length = 100)
    private String weather;

    @Column(name = "home_score")
    private Integer homeScore;

    @Column(name = "away_score")
    private Integer awayScore;

    @Column(name = "home_xg")
    private Double homeXG;

    @Column(name = "away_xg")
    private Double awayXG;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    @Builder.Default
    private MatchStatus status = MatchStatus.SCHEDULED;

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MatchExternalId> externalIds = new ArrayList<>();
}
