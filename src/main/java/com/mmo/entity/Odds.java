package com.mmo.entity;

import com.mmo.entity.abs.AbstractEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "odds", indexes = {
        @Index(name = "idx_match_provider", columnList = "match_id,provider_id")
})
@Getter
@Setter
public class Odds extends AbstractEntity {

    @ManyToOne
    private Match match;

//    @ManyToOne
//    private OddsProvider provider;

//    @Enumerated(EnumType.STRING)
//    private MarketType marketType;

    private Double homeOdds;
    private Double drawOdds;
    private Double awayOdds;

    private Double overOdds;
    private Double underOdds;

    private Double handicap;
}