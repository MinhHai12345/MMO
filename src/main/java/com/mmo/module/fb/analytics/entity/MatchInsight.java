package com.mmo.module.fb.analytics.entity;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.match.entity.Match;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "fb_match_insights")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchInsight extends AbstractEntity {

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Column(name = "home_avg_xg")
    private Double homeAvgXg;

    @Column(name = "home_avg_xg_conceded")
    private Double homeAvgXgConceded;

    @Column(name = "away_avg_xg")
    private Double awayAvgXg;

    @Column(name = "away_avg_xg_conceded")
    private Double awayAvgXgConceded;

    @Column(name = "home_form", length = 32)
    private String homeForm;

    @Column(name = "away_form", length = 32)
    private String awayForm;

    @Column(name = "home_rest_days")
    private Integer homeRestDays;

    @Column(name = "away_rest_days")
    private Integer awayRestDays;

    @Column(columnDefinition = "TEXT")
    private String h2hSummaryJson;

    @Column(columnDefinition = "TEXT")
    private String lineupJson;

    @Column(name = "fetched_at")
    private Instant fetchedAt;
}
