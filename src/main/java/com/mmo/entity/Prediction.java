package com.mmo.entity;

import com.mmo.entity.abs.AbstractEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "predictions")
@Getter
@Setter
public class Prediction extends AbstractEntity {

    @ManyToOne
    private Match match;

//    private String modelVersion;
//
//    @Enumerated(EnumType.STRING)
//    private PredictionType predictionType;

    private Double probabilityHome;
    private Double probabilityDraw;
    private Double probabilityAway;

    private Double confidenceScore;

}
