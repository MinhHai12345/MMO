package com.mmo.module.fb.domain.entity;

import com.mmo.core.entity.AbstractEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "fb_odds_history")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OddsHistory extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_odds_id", nullable = false)
    private MatchOdds matchOdds;

    @Column(precision = 8, scale = 3)
    private BigDecimal homeWinOdds;

    @Column(precision = 8, scale = 3)
    private BigDecimal drawOdds;

    @Column(precision = 8, scale = 3)
    private BigDecimal awayWinOdds;

    @Column(name = "recorded_at")
    private Instant recordedAt;
}
