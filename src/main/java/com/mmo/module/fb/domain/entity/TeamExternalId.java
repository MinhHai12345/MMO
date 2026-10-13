package com.mmo.module.fb.domain.entity;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.domain.enums.Provider;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fb_team_external_ids",
       uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "provider"}))
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TeamExternalId extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;

    @Column(name = "external_id", nullable = false)
    private String externalId;
}
