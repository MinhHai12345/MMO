package com.mmo.entity;

import com.mmo.entity.abs.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "match_features")
public class MatchFeature extends AbstractEntity {

    @Column
    private String featureKey;

    @Column
    private Double featureValue;

    @ManyToOne
    @JoinColumn(name = "match_id")
    private Match match;
}
