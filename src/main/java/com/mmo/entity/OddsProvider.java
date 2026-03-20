package com.mmo.entity;

import com.mmo.entity.abs.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "odds_providers", indexes = {
        @Index(name = "idx_provider_code", columnList = "code"),
        @Index(name = "idx_provider_active", columnList = "isActive")
})
@Getter
@Setter
public class OddsProvider extends AbstractEntity {

    @Column
    private String name;

    @Column
    private String code;

    @Column
    private String apiUrl;

    @Column
    private Integer priority;

    @Column
    private Boolean isActive = true;
}
