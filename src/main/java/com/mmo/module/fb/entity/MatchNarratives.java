package com.mmo.module.fb.entity;

import com.mmo.entity.AbstractEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "fb_match_narratives")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchNarratives extends AbstractEntity {

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
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
