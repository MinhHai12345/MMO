package com.mmo.module.fb.match.entity;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.league.entity.Season;
import com.mmo.module.fb.match.enums.MatchStatus;
import com.mmo.module.fb.team.entity.Team;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Canonical fixture aggregate. Provider IDs and enrichment execution state are separate
 * records; the legacy sync timestamps remain temporarily for backward compatibility.
 */
@Entity
@Table(name = "fb_matches",
        indexes = {
                @Index(name = "idx_match_time", columnList = "match_time"),
                @Index(name = "idx_match_status_time", columnList = "status, match_time"),
                @Index(name = "idx_match_season_teams_time",
                        columnList = "season_id, home_team_id, away_team_id, match_time")
        })
@org.hibernate.annotations.Check(constraints = "home_team_id <> away_team_id")
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

    @Column(name = "home_half_time_score")
    private Integer homeHalfTimeScore;

    @Column(name = "away_half_time_score")
    private Integer awayHalfTimeScore;

    @Column(name = "half_time_result", length = 1)
    private String halfTimeResult;

    @Column(name = "full_time_result", length = 1)
    private String fullTimeResult;

    /** Canonical fixture-level xG projection; provider-specific values are retained in source snapshots. */
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

    /** Provider/resource sync state is part of the aggregate relationship, not Match columns. */
    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MatchEnrichmentState> enrichmentStates = new ArrayList<>();

    @Version
    @Column(name = "version")
    private Long version;

    public boolean hasFinalScore() {
        return homeScore != null && awayScore != null;
    }

    public boolean isFinished() {
        return status == MatchStatus.FINISHED && hasFinalScore();
    }

    public void addExternalId(MatchExternalId externalId) {
        if (externalId == null) return;
        externalIds.add(externalId);
        externalId.setMatch(this);
    }

    public void removeExternalId(MatchExternalId externalId) {
        if (externalId == null) return;
        externalIds.remove(externalId);
        externalId.setMatch(null);
    }
}
