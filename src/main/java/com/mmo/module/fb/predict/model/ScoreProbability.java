package com.mmo.module.fb.predict.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScoreProbability implements Comparable<ScoreProbability>{
    private String score;
    private double probability;

    @Override
    public int compareTo(ScoreProbability o) {
        return Double.compare(o.probability, this.probability);
    }
}
