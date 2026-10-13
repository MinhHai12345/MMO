package com.mmo.module.fb.domain.entity;

import com.mmo.core.entity.AbstractEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "fb_team_form",
       uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "season_id", "form_type"}))
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TeamForm extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @Column(name = "form_type", nullable = false)
    private String formType;

    @Column(name = "form_data")
    private String formData;

    @Column(name = "calculated_at")
    private Instant calculatedAt;
}
