package com.mmo.module.fb.domain.league.entity;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.domain.team.entity.Team;
import com.mmo.module.fb.domain.league.enums.StandingType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "fb_league_standings",
        uniqueConstraints = @UniqueConstraint(name = "uk_standing_season_team_type_group",
                columnNames = {"season_id", "team_id", "standing_type", "group_key"}),
        indexes = {
                @Index(name = "idx_fb_standing_season_type", columnList = "season_id, standing_type"),
                @Index(name = "idx_fb_standing_season_group", columnList = "season_id, group_key")
        })
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LeagueStanding extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Enumerated(EnumType.STRING)
    @Column(name = "standing_type", nullable = false, length = 16, columnDefinition = "varchar(16) default 'TOTAL'")
    @Builder.Default
    private StandingType standingType = StandingType.TOTAL;

    @Column(name = "group_name", length = 100)
    private String groupName;

    /**
     * Non-null normalized group discriminator. Empty string represents an ungrouped table,
     * avoiding MySQL's multiple-NULL behavior for unique indexes.
     */
    @Column(name = "group_key", nullable = false, length = 100, columnDefinition = "varchar(100) default ''")
    @Builder.Default
    private String groupKey = "";

    @Column(name = "position_rank")
    private Integer position;

    private Integer points;
    private Integer played;
    private Integer won;
    private Integer drawn;
    private Integer lost;
    private Integer goalsFor;
    private Integer goalsAgainst;
    private Integer goalDifference;

    @Column(length = 32)
    private String form;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;
}
