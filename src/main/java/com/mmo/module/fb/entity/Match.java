package com.mmo.module.fb.entity;

import com.mmo.entity.AbstractEntity;
import com.mmo.module.fb.entity.enums.MatchStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "matches")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Match extends AbstractEntity {

    @Column
    private String slug;

    @Column
    private Integer homeScore;

    @Column
    private Integer awayScore;

    @Column
    private Long matchTime;

    @Column(precision = 5, scale = 2)
    private BigDecimal homeXG;

    @Column(precision = 5, scale = 2)
    private BigDecimal awayXG;

    @ManyToOne
    private League league;

    @ManyToOne
    private Team homeTeam;

    @ManyToOne
    private Team awayTeam;

    @Enumerated(EnumType.STRING)
    private MatchStatus status;

    @Column
    private Long sofaScoreId;

    @Column
    private int round;

    @Column
    private boolean notifiedPredict = false;

    @Column
    private boolean notifiedResult = false;

    @OneToOne(mappedBy = "match")
    private MatchOdds matchOdds;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id")
    private Season season;

    @Transient
    private Long sofaScoreHomeTeamId;

    @Transient
    private Long sofaScoreAwayTeamId;


}