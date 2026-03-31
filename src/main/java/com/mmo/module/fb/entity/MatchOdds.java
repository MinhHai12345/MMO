package com.mmo.module.fb.entity;

import com.mmo.entity.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "match_odds")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MatchOdds extends AbstractEntity {

    @ManyToOne
    @JoinColumn(name = "match_id")
    private Match match;

    @Column(precision = 5, scale = 2)
    private BigDecimal homeOdds;

    @Column(precision = 5, scale = 2)
    private BigDecimal drawOdds;

    @Column(precision = 5, scale = 2)
    private BigDecimal awayOdds;

    @Column(precision = 5, scale = 2)
    private BigDecimal overOdds;

    @Column(precision = 5, scale = 2)
    private BigDecimal underOdds;

    @Column(precision = 5, scale = 2)
    private BigDecimal handicap;
}