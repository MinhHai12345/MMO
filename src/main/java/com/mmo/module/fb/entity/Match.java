package com.mmo.module.fb.entity;

import com.mmo.entity.AbstractEntity;
import com.mmo.module.fb.entity.enums.MatchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "matches", indexes = {
        @Index(name = "idx_match_time", columnList = "matchTime"),
        @Index(name = "idx_status", columnList = "status")
})
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Match extends AbstractEntity {

    @Column
    private String homeScore;

    @Column
    private String awayScore;

    @Column
    private LocalDateTime matchTime;

    @Column
    private String homeXG;

    @Column
    private String awayXG;

    @Column
    private String winProbability;

    @Column
    private String drawProbability;

    @Column
    private String lossProbability;

    @ManyToOne
    private League league;

    @ManyToOne
    private Team homeTeam;

    @ManyToOne
    private Team awayTeam;

    @Enumerated(EnumType.STRING)
    private MatchStatus status;

    @Column
    private String underStatMatchId;

}