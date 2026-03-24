package com.mmo.module.fb.entity;

import com.mmo.entity.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "players")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Player extends AbstractEntity {

    @Column
    private String name;

    @Column
    private String position; // F, M, D, G

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column
    private String underStatId;

    @Column
    private double xG;

    @Column
    private double xA;

    @Column
    private int goals;

    @Column
    private int assists;
}
