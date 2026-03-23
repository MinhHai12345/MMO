package com.mmo.entity;

import com.mmo.entity.abs.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "match_advance_stats")
@Getter
@Setter
public class MatchAdvanceStats extends AbstractEntity {

    @OneToOne
    @MapsId
    private Match match;

    @Column
    private double homeXG;

    @Column
    private double awayXG;

    @Column
    private int homePpdaAtt;

    @Column
    private int homePpdaDef;

    @Column
    private int awayPpdaAtt;

    @Column
    private int awayPpdaDef;

    @Column
    private int homeDeep;

    @Column
    private int awayDeep;

    @Column
    private double homeXPts;

    @Column
    private double awayXPts;
}
