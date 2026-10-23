package com.mmo.module.fb.monetization.entity;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.analytics.entity.MatchPrediction;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "fb_tip_transactions", indexes = {
        @Index(name = "idx_fb_tip_subscriber", columnList = "subscriber_id"),
        @Index(name = "idx_fb_tip_prediction", columnList = "match_prediction_id"),
        @Index(name = "idx_fb_tip_delivery_status", columnList = "delivery_status")
})
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class TipTransaction extends AbstractEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscriber_id", nullable = false)
    private Subscriber subscriber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_prediction_id", nullable = false)
    private MatchPrediction matchPrediction;

    @Column(name = "delivery_status")
    @Builder.Default
    private String deliveryStatus = "PENDING";

    @Column(name = "delivery_platform")
    private String deliveryPlatform;

    @Column(name = "delivered_at")
    private Instant deliveredAt;
}
