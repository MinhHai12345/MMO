package com.mmo.entity;

import com.mmo.entity.abs.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "teams")
public class Team extends AbstractEntity {

    @Column
    private String name;

    @Column
    private String country;

    @OneToMany(mappedBy = "homeTeam")
    private Set<Match> homeMatches = new HashSet<>();

    @OneToMany(mappedBy = "awayTeam")
    private Set<Match> awayMatches = new HashSet<>();

    @OneToMany(mappedBy = "team")
    private Set<Player> players = new HashSet<>();
}