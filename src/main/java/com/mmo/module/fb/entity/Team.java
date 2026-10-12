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
@Table(name = "fb_teams")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Team extends AbstractEntity {

    @Column(nullable = false)
    private String name;

    @Column
    private String shortName;

    @Column
    private String slug;

    @Column
    private String logoUrl;

    @Column
    private Integer ranking;

    @Column
    private String externalId;

}