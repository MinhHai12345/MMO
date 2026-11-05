package com.mmo.module.fb.domain.match.entity;

import com.mmo.core.entity.AbstractEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Historical odds snapshot. Legacy 1X2 columns remain readable; normalized quote
 * history uses quote/outcome/line/odd so every market can be audited.
 */
@Entity
@org.hibernate.annotations.Immutable
@Table(name = "fb_odds_history",
        indexes = {
                @Index(name = "idx_fb_odds_history_snapshot", columnList = "match_odds_id, recorded_at"),
                @Index(name = "idx_fb_odds_history_recorded_at", columnList = "recorded_at"),
                @Index(name = "idx_fb_odds_history_quote", columnList = "match_odds_quote_id, recorded_at")
        })
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OddsHistory extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_odds_id")
    private MatchOdds matchOdds;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_odds_quote_id")
    private MatchOddsQuote quote;

    @Column(length = 40)
    private String outcome;

    @Column(name = "line_value", precision = 10, scale = 3)
    private BigDecimal line;

    @Column(precision = 10, scale = 4)
    private BigDecimal odd;

    @Column(precision = 8, scale = 3)
    private BigDecimal homeWinOdds;
    @Column(precision = 8, scale = 3)
    private BigDecimal drawOdds;
    @Column(precision = 8, scale = 3)
    private BigDecimal awayWinOdds;

    @Column(name = "recorded_at")
    private Instant recordedAt;

    @PrePersist
    private void validateSnapshot() {
        if (matchOdds == null && quote == null) {
            throw new IllegalStateException("Odds history must reference a legacy odds row or normalized quote");
        }
        if (recordedAt == null) recordedAt = Instant.now();
    }
}
