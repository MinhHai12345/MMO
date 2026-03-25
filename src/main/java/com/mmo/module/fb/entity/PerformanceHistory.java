package com.mmo.module.fb.entity;

import com.mmo.entity.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "performance_histories")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PerformanceHistory extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(name = "team_id", updatable = false, insertable = false)
    private Long teamId;

    @Column
    private String homeAway;

    @Column
    private Double xG;

    @Column
    private Double xGA;

    @Column
    private Double npxG;

    @Column
    private Double npxGA;

    @Column
    private Integer ppdaAtt;

    @Column
    private Integer ppdaDef;

    @Column
    private Integer ppdaAllowedAtt;

    @Column
    private Integer ppdaAllowedDef;

    @Column
    private Integer deep;

    @Column
    private Integer deepAllowed;

    @Column
    private Integer scored;

    @Column
    private Integer missed;

    @Column
    private Double xpts;

    @Column
    private String result;

    @Column
    private LocalDateTime date;

    @Column
    private Double npxGD;
}