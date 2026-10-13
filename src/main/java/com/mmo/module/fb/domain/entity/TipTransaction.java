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
@Table(name = "fb_tip_transactions")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TipTransaction extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscriber_id", nullable = false)
    private Subscriber subscriber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_prediction_id", nullable = false)
    private MatchPrediction matchPrediction;

    @Column(name = "delivery_status")
    private String deliveryStatus = "PENDING";

    @Column(name = "delivery_platform")
    private String deliveryPlatform;

    @Column(name = "delivered_at")
    private Instant deliveredAt;
}
