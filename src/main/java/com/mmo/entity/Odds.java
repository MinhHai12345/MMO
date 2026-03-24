package com.mmo.entity;

import com.mmo.entity.abs.AbstractEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "odds")
//@Table(name = "odds", indexes = {
//        @Index(name = "idx_match_provider", columnList = "match_id,provider_id")
//})
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
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