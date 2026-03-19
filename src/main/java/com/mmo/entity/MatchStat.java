package com.mmo.entity;

import com.mmo.entity.abs.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;


@Getter
@Setter
@Entity
@Table(name = "match_stats")
@FieldNameConstants
public class MatchStat extends AbstractEntity {

    @Column
    private String statKey;

    @Column
    private Double statValue;

    @Column
    private String source;

    @ManyToOne
    @JoinColumn(name = "match_id")
    private Match match;

    @ManyToOne
    @JoinColumn(name = "team_id")
    private Team team;

}
