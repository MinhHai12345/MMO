package com.mmo.entity;

import com.mmo.entity.abs.AbstractEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "team_performances")
@Getter
@Setter
public class TeamPerformance extends AbstractEntity {

    @ManyToOne
    private Team team;

    @ManyToOne
    private Match match;

    private double xG;
    private double xGA;
    private int goalsScored;
    private int goalsMissed;
    private double ppdaValue;
    private int deepPasses;

    private LocalDateTime matchDate;
}