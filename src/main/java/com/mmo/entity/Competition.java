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
@Table(name = "competitions")
public class Competition extends AbstractEntity {

    @Column
    private String name;

    @Column
    private String country;

    @Column
    private String type;

    @OneToMany(mappedBy = "competition")
    private Set<Match> matches = new HashSet<>();
}
