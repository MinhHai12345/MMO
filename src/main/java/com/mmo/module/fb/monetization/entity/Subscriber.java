package com.mmo.module.fb.monetization.entity;

import com.mmo.core.entity.AbstractEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "fb_subscribers", uniqueConstraints = @UniqueConstraint(name = "uk_fb_subscriber_user", columnNames = "user_id"))
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class Subscriber extends AbstractEntity {
    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_plan_id", nullable = false)
    private SubscriptionPlan subscriptionPlan;

    @Column(name = "telegram_id")
    private Long telegramId;

    @Column(name = "twitter_handle")
    private String twitterHandle;

    private String email;

    @Column(name = "subscription_status")
    @Builder.Default
    private String subscriptionStatus = "ACTIVE";

    @Column(name = "start_date")
    private Instant startDate;

    @Column(name = "end_date")
    private Instant endDate;

    @Column(name = "auto_renew")
    @Builder.Default
    private Boolean autoRenew = true;

    @OneToMany(mappedBy = "subscriber", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TipTransaction> tipTransactions = new ArrayList<>();

    @OneToMany(mappedBy = "subscriber", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AffiliateTracking> affiliateTrackings = new ArrayList<>();
}
