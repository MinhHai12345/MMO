package com.mmo.entity;

import com.mmo.entity.abs.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "raw_data")
public class RawData extends AbstractEntity {

    @Column
    private String source;

    @Column
    private String endpoint;

    @Column(columnDefinition = "jsonb")
    private String rawJson;
}