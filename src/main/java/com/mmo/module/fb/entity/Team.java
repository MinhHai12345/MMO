package com.mmo.module.fb.entity;

import com.mmo.entity.AbstractEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "teams")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Team extends AbstractEntity {

    private String name;
    private String shortName;
    private String logoUrl;

}