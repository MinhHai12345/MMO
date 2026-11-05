package com.mmo.module.fb.domain.match.entity;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.match.enums.MatchOddsMarketType;
import com.mmo.module.fb.domain.match.enums.MatchOddsPeriod;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Normalized single-outcome odds quote. A row represents exactly one provider,
 * bookmaker, market, line, period and outcome; unlike legacy MatchOdds it supports
 * arbitrary totals/handicap lines and markets without adding columns.
 *
 * A zero line is used for markets that do not have a line so the unique key remains
 * deterministic on MySQL (where NULL values do not collide in unique indexes).
 */
@Entity
@Table(name = "fb_match_odds_quotes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_fb_match_odds_quote_identity",
                columnNames = {"match_id", "bookmaker_id", "provider", "market_type",
                        "market_period", "line_value", "outcome"}),
        indexes = {
                @Index(name = "idx_fb_match_odds_quote_match",
                        columnList = "match_id, market_type, market_period"),
                @Index(name = "idx_fb_match_odds_quote_provider",
                        columnList = "provider, fetched_at")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchOddsQuote extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bookmaker_id", nullable = false)
    private Bookmaker bookmaker;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MatchProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "market_type", nullable = false, length = 32)
    private MatchOddsMarketType marketType;

    @Enumerated(EnumType.STRING)
    @Column(name = "market_period", nullable = false, length = 24)
    @Builder.Default
    private MatchOddsPeriod marketPeriod = MatchOddsPeriod.UNKNOWN;

    @Column(name = "line_value", nullable = false, precision = 10, scale = 3)
    @Builder.Default
    private BigDecimal line = BigDecimal.ZERO.setScale(3);

    @Column(nullable = false, length = 40)
    private String outcome;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal odd;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    @PreUpdate
    private void validateQuote() {
        if (match == null || bookmaker == null || provider == null || marketType == null
                || marketPeriod == null || outcome == null || outcome.isBlank()
                || odd == null || odd.signum() <= 0) {
            throw new IllegalStateException("Odds quote identity and a positive decimal odd are required");
        }
        outcome = outcome.trim().toUpperCase(java.util.Locale.ROOT);
        if (outcome.length() > 40) outcome = outcome.substring(0, 40);
        line = (line == null ? BigDecimal.ZERO : line).setScale(3, RoundingMode.HALF_UP);
        if (fetchedAt == null) fetchedAt = Instant.now();
    }
}
