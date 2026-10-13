package com.mmo.module.fb.domain.entity;

import com.mmo.core.entity.AbstractEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "fb_match_advanced_stats",
       uniqueConstraints = @UniqueConstraint(columnNames = {"match_id", "team_id"}))
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MatchAdvancedStats extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    private BigDecimal ppda;

    @Column(name = "ppda_att")
    private Integer ppdaAtt;

    @Column(name = "ppda_def")
    private Integer ppdaDef;

    @Column(name = "field_tilt")
    private BigDecimal fieldTilt;

    @Column(name = "xg_chain")
    private BigDecimal xgChain;

    @Column(name = "xg_buildup")
    private BigDecimal xgBuildup;

    private BigDecimal possession;

    @Column(name = "deep_completions")
    private Integer deepCompletions;

    @Column(name = "final_third_entries")
    private Integer finalThirdEntries;

    @Column(name = "shots_from_box")
    private Integer shotsFromBox;

    @Column(name = "shots_outside_box")
    private Integer shotsOutsideBox;

    @Column(name = "set_piece_xg")
    private BigDecimal setPieceXg;

    @Column(name = "counter_attack_xg")
    private BigDecimal counterAttackXg;
}
