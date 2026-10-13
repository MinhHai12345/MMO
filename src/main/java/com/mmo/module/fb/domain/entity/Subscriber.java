package com.mmo.module.fb.domain.entity;

import com.mmo.core.entity.AbstractEntity;
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
@Table(name = "fb_subscribers", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id"}))
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Subscriber extends AbstractEntity {

    @Column(nullable = false)
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
    private String subscriptionStatus = "ACTIVE";

    @Column(name = "start_date")
    private Instant startDate;

    @Column(name = "end_date")
    private Instant endDate;

    @Column(name = "auto_renew")
    private Boolean autoRenew = true;

    @OneToMany(mappedBy = "subscriber", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TipTransaction> tipTransactions = new ArrayList<>();

    @OneToMany(mappedBy = "subscriber", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AffiliateTracking> affiliateTrackings = new ArrayList<>();
}
