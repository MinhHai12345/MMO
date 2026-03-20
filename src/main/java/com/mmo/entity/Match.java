package com.mmo.entity;

import com.mmo.entity.abs.AbstractEntity;
import com.mmo.entity.enums.MatchStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
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

    @ManyToOne
    private Competition competition;

    @ManyToOne
    private Team homeTeam;

    @ManyToOne
    private Team awayTeam;

    private LocalDateTime matchTime;

    @Enumerated(EnumType.STRING)
    private MatchStatus status;

    private Integer homeScore;
    private Integer awayScore;
}