package com.mmo.module.fb.match.entity;

import com.mmo.core.entity.AbstractEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Legacy grouped odds projection retained for existing API/report consumers.
 * New ingestion writes normalized one-outcome MatchOddsQuote rows as well.
 */
@Entity
@Table(name = "fb_match_odds",
        uniqueConstraints = @UniqueConstraint(name = "uk_fb_match_bookmaker_market",
                columnNames = {"match_id", "bookmaker_id", "market_name"}),
        indexes = @Index(name = "idx_fb_match_odds_match", columnList = "match_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchOdds extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bookmaker_id", nullable = false)
    private Bookmaker bookmaker;

    @Column(name = "market_name", nullable = false, length = 80)
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

    /** Number of source bookmakers used to calculate aggregate odds, when supplied. */
    @Column(name = "bookmaker_count")
    private Integer bookmakerCount;

    @Column(name = "asian_handicap_line", precision = 8, scale = 3)
    private BigDecimal asianHandicapLine;
    @Column(precision = 8, scale = 3)
    private BigDecimal asianHandicapHome;
    @Column(precision = 8, scale = 3)
    private BigDecimal asianHandicapAway;

    @Builder.Default
    @OneToMany(mappedBy = "matchOdds", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OddsHistory> history = new ArrayList<>();

    @Version
    @Column(name = "version")
    private Long version;

    public void addHistory(OddsHistory snapshot) {
        if (snapshot == null) return;
        history.add(snapshot);
        snapshot.setMatchOdds(this);
    }
}
