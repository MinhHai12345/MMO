package com.mmo.entity;

import com.mmo.entity.abs.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

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
    private String season;
}