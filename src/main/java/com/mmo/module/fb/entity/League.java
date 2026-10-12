package com.mmo.module.fb.entity;

import com.mmo.entity.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fb_leagues")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class League extends AbstractEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private boolean active;

    @Column
    private String externalId;

    @Column
    private String country;

    @Column
    private String logoUrl;

}