package com.mmo.entity;

import com.mmo.entity.abs.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "value_bets")
public class ValueBet extends AbstractEntity {

    @Column
    private String selection;

    @Column
    private Double modelProb;

    @Column
    private Double marketProb;

    @Column
    private Double edge;

    @ManyToOne
    @JoinColumn(name = "match_id")
    private Match match;
}