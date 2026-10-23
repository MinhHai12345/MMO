package com.mmo.module.fb.analytics.entity;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.match.entity.Match;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fb_match_narratives")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchNarratives extends AbstractEntity {

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Column(columnDefinition = "TEXT")
    private String keyAbsences;

    @Column(columnDefinition = "TEXT")
    private String playerToWatch;

    @Column(columnDefinition = "TEXT")
    private String rivalryContext;

    @Column(columnDefinition = "TEXT")
    private String refereeInfo;
}
