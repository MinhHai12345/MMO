package com.mmo.module.fb.domain.entity;

import com.mmo.core.entity.AbstractEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "fb_match_odds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchOdds extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bookmaker_id", nullable = false)
    private Bookmaker bookmaker;

    @Column(name = "market_name", nullable = false)
    private String marketName;

    @Column(precision = 8, scale = 3)
    private BigDecimal homeWinOdds;

    @Column(precision = 8, scale = 3)
    private BigDecimal drawOdds;

    @Column(precision = 8, scale = 3)
    private BigDecimal awayWinOdds;

    @Column(precision = 8, scale = 3)
    private BigDecimal over25Odds;

    @Column(precision = 8, scale = 3)
    private BigDecimal under25Odds;

    @Column(precision = 8, scale = 3)
    private BigDecimal bttsYesOdds;

    @Column(precision = 8, scale = 3)
    private BigDecimal bttsNoOdds;

    @Column(precision = 8, scale = 3)
    private BigDecimal asianHandicapHome;

    @Column(precision = 8, scale = 3)
    private BigDecimal asianHandicapAway;

    @Builder.Default
    @OneToMany(mappedBy = "matchOdds", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OddsHistory> history = new ArrayList<>();

}
