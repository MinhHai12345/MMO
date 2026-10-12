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

@Entity
@Table(name = "fb_match_odds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchOdds extends AbstractEntity {

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    private Match match;

    @Column(precision = 5, scale = 2)
    private Double homeWinOdds;

    @Column(precision = 5, scale = 2)
    private Double drawOdds;

    @Column(precision = 5, scale = 2)
    private Double awayWinOdds;

    @Column
    private String handicapLine;

    @Column(precision = 5, scale = 2)
    private Double homeHandicapOdds;

    @Column(precision = 5, scale = 2)
    private Double awayHandicapOdds;

    @Column(precision = 5, scale = 2)
    private Double overUnderLine;

    @Column(precision = 5, scale = 2)
    private Double overOdds;

    @Column(precision = 5, scale = 2)
    private Double underOdds;

}
