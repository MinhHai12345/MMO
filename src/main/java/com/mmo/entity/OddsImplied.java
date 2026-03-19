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
@Table(name = "odds_implied")
public class OddsImplied extends AbstractEntity {

    @Column
    private String selection;

    @Column
    private Double impliedProb;

    @Column
    private Double normalizedProb;

    @ManyToOne
    @JoinColumn(name = "match_id")
    private Match match;
}
