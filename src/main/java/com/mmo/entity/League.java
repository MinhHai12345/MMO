package com.mmo.entity;

import com.mmo.entity.abs.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "leagues")
@Getter
@Setter
public class League extends AbstractEntity {

    @Column
    private String name;

    @Column
    private String slug;

    @Column
    private String country;

    @Column
    private String season;
}