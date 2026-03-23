package com.mmo.entity;

import com.mmo.entity.abs.AbstractEntity;
import com.mmo.entity.enums.MatchStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "matches", indexes = {
        @Index(name = "idx_match_time", columnList = "matchTime"),
        @Index(name = "idx_status", columnList = "status")
})
@Getter
@Setter
public class Match extends AbstractEntity {

    @Column
    private Integer homeScore;

    @Column
    private Integer awayScore;

    @Column
    private String matchTime;

    @ManyToOne
    private League league;

    @ManyToOne
    private Team homeTeam;

    @ManyToOne
    private Team awayTeam;

    @Enumerated(EnumType.STRING)
    private MatchStatus status;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private MatchAdvanceStats advanceStats;

    @Column
    private String underStatMatchId;

}