package com.mmo.module.fb.entity;

import com.mmo.entity.AbstractEntity;
import com.mmo.module.fb.entity.enums.MatchPredictionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Entity
@Table(name = "match_predictions", indexes = {
        @Index(name = "idx_kickoff_time", columnList = "kickoff_time"),
        @Index(name = "idx_status", columnList = "status")
})
@Getter
@Setter
public class MatchPrediction extends AbstractEntity {

    @OneToOne
    @JoinColumn(name = "match_id")
    private Match match;

    @Column(nullable = false)
    private LocalDateTime kickoffTime;

    @Transient
    private String matchTime;

    @Transient
    private int index;

    @Transient
    private boolean isWin;

    @Transient
    private Double tempHomeProb;

    @Transient
    private Double tempDrawProb;

    @Transient
    private Double tempAwayProb;


    // =========================================================================
    // 🏛️ MARKET ODDS (Dữ liệu tỷ lệ cược cào từ SofaScore)
    // =========================================================================
    @Column(precision = 2)
    private Double sofaHomeOdd;

    @Column(precision = 2)
    private Double sofaDrawOdd;

    @Column(precision = 2)
    private Double sofaAwayOdd;

    // =========================================================================
    // 🔬 POISSON PREDICTION RESULT (Dữ liệu hệ thống tự tính toán)
    // =========================================================================
    @Column(precision = 2)
    private Double marketHomeXG;

    @Column(precision = 2)
    private Double marketAwayXG;

    @Column(precision = 2)
    private Double h2TotalXG;

    @Column(precision = 2)
    private String h2HandicapMargin;

    @Column(precision = 2)
    private Double h2ProbUnder25;

    @Column(precision = 2)
    private Double h2ProbOver25;

    @Column(precision = 2)
    private Double expectedHomeGoals;

    @Column(precision = 2)
    private Double expectedAwayGoals;

    @Column
    private Integer actualHomeGoals;

    @Column
    private Integer actualAwayGoals;

    @Column(precision = 2)
    private Double fairHomeOdd;

    @Column(precision = 2)
    private Double fairDrawOdd;

    @Column(precision = 2)
    private Double fairAwayOdd;

    // =========================================================================
    // 🎯 BUSINESS LOGIC & CONTENT DISTRIBUTION STATE
    // =========================================================================
    @Column
    private String valueBetPick;

    @Column
    private String mostLikelyWinner;

    @Column(precision = 2)
    private Double smartStakingSize;

    @Column(precision = 2)
    private Double edgePercentage;

    @Column
    private String topCorrectScores;

    @Column
    private boolean hasValue = false;

    @Column
    private boolean isPremium = false;

    @Column
    @Enumerated(EnumType.STRING)
    private MatchPredictionStatus status; // Trạng thái: PENDING (Chưa tính), READY (Đã tính), FREE_DASHBOARD, FREE_DETAIL, VIP_ONLY, POSTED (Đã đăng thành công)

    public String getMatchTime() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);
        ZonedDateTime utcTime = kickoffTime
                .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                .withZoneSameInstant(ZoneOffset.UTC);
        this.matchTime = utcTime.format(formatter);
        return matchTime;
    }
}
