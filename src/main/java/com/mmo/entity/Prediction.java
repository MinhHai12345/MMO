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
@Table(name = "predictions")
public class Prediction extends AbstractEntity {

    @Column
    private String modelVersion;

    @Column
    private String prediction;

    @Column
    private Double confidence;

    @ManyToOne
    @JoinColumn(name = "match_id")
    private Match match;
}