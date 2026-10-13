package com.mmo.module.fb.domain.entity;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.domain.enums.MatchStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "fb_matches",
        indexes = {
                @Index(name = "idx_match_time", columnList = "match_time"),
                @Index(name = "idx_match_status", columnList = "status"),
                @Index(name = "idx_ext_id", columnList = "external_id", unique = true)
        })
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Match extends AbstractEntity {

    @Column(nullable = false, unique = true)
    private String externalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Season season;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Team homeTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Team awayTeam;

    @Column
    private Long startTimestamp;

    @Column(nullable = false)
    private Instant matchTime;

    @Column
    private Integer round;

    @Column(name = "round_info")
    private String roundInfo;

    private String venue;

    private Integer attendance;

    private String referee;

    private String weather;

    @Column
    private Integer homeScore;

    @Column
    private Integer awayScore;

    @Column(precision = 5, scale = 2)
    private Double homeXG;

    @Column(precision = 5, scale = 2)
    private Double awayXG;

    @Enumerated(EnumType.STRING)
    private MatchStatus status;

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MatchExternalId> externalIds = new ArrayList<>();

}