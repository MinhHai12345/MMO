package com.mmo.module.fb.entity;

import com.mmo.entity.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "leagues")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class League extends AbstractEntity {

    @Column
    private String name;

    @Column
    private String slug;

    @Column
    private boolean isActive;

    @Column
    private Long sofaScoreId;

    @Column(name = "current_season_id")
    private Long currentSeasonId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_season_id", insertable = false, updatable = false)
    private Season currentSeason;

    @OneToMany(mappedBy = "league", fetch = FetchType.LAZY)
    private Set<Season> seasons = new HashSet<>();

}