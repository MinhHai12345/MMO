package com.mmo.entity;

import com.mmo.entity.abs.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "odds_raw")
public class OddsRaw extends AbstractEntity {

    @Column
    private String bookmaker;

    @Column
    private String market;

    @Column
    private String selection;

    @Column
    private Double odds;

    @Column
    private LocalDateTime timestamp;

    @ManyToOne
    @JoinColumn(name = "match_id")
    private Match match;
}
