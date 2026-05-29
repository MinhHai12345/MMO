package com.mmo.module.fb.entity;

import com.mmo.entity.AbstractEntity;
import com.mmo.module.fb.entity.enums.MatchPredictionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    // =========================================================================
    // 🏛️ MARKET ODDS (Dữ liệu tỷ lệ cược cào từ SofaScore)
    // =========================================================================
    @Column
    private Double sofaHomeOdd;

    @Column
    private Double sofaDrawOdd;

    @Column
    private Double sofaAwayOdd;

    // =========================================================================
    // 🔬 POISSON PREDICTION RESULT (Dữ liệu hệ thống tự tính toán)
    // =========================================================================
    @Column
    private Double expectedHomeGoals;

    @Column
    private Double expectedAwayGoals;

    @Column
    private Double fairHomeOdd;

    @Column
    private Double fairDrawOdd;

    @Column
    private Double fairAwayOdd;

    // =========================================================================
    // 🎯 BUSINESS LOGIC & CONTENT DISTRIBUTION STATE
    // =========================================================================
    @Column
    private Double edgePercentage; // Độ lệch % lợi thế so với nhà cái (Ví dụ: 8.5)

    @Column
    private boolean hasValue = false; // Trận đấu này có đáng đầu tư hay không?

    @Column
    private boolean isPremium = false; // Thuộc gói VIP (True) hay gói Free (False)

    @Column
    private MatchPredictionStatus status; // Trạng thái: PENDING (Chưa tính), READY (Đã tính), FREE_DASHBOARD, FREE_DETAIL, VIP_ONLY, POSTED (Đã đăng thành công)

}
