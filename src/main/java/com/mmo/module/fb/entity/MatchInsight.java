package com.mmo.module.fb.entity;

import com.mmo.entity.AbstractEntity;
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
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    private Match match;

    @Column(precision = 5, scale = 2)
    private Double homeAvgXg;

    @Column(precision = 5, scale = 2)
    private Double homeAvgXgConceded;

    @Column(precision = 5, scale = 2)
    private Double awayAvgXg;

    @Column
    private Double awayAvgXgConceded;

    @Column
    private String homeForm;

    @Column
    private String awayForm;

    @Column
    private Integer homeRestDays;

    @Column
    private Integer awayRestDays;

    @Column(columnDefinition = "TEXT")
    private String h2hSummaryJson;

    @Column(columnDefinition = "TEXT")
    private String lineupJson;

    @Column
    private Instant fetchedAt;
}
