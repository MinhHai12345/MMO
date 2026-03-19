package com.mmo.entity;

import com.mmo.entity.abs.AbstractEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "matches")
@FieldNameConstants
public class Match extends AbstractEntity {
    public static final String COMPETITION_ID = "competition_id";

    @Column
    private String season;

    @Column
    private LocalDateTime matchTime;

    @Column
    private String status;

    @ManyToOne
    @JoinColumn(name = COMPETITION_ID)
    private Competition competition;

    @ManyToOne
    @JoinColumn(name = "home_team_id")
    private Team homeTeam;

    @ManyToOne
    @JoinColumn(name = "away_team_id")
    private Team awayTeam;

    @OneToMany(mappedBy = "match")
    private List<MatchStat> stats;

    @OneToMany(mappedBy = "match")
    private List<OddsRaw> odds;

    @OneToMany(mappedBy = "match")
    private List<Prediction> predictions;
}