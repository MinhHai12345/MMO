package com.mmo.module.fb.match.entity;

import com.mmo.core.entity.AbstractEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fb_bookmakers",
        uniqueConstraints = @UniqueConstraint(name = "uk_fb_bookmaker_name", columnNames = "name"))
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Bookmaker extends AbstractEntity {

    @Column(nullable = false)
    private String name;

    private String website;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
}
