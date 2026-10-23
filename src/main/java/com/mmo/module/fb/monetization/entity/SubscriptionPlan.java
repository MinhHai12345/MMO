package com.mmo.module.fb.monetization.entity;

import com.mmo.core.entity.AbstractEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "fb_subscription_plans",
        uniqueConstraints = @UniqueConstraint(name = "uk_fb_subscription_plan_name", columnNames = "name"),
        indexes = @Index(name = "idx_fb_subscription_plan_active", columnList = "is_active"))
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class SubscriptionPlan extends AbstractEntity {
    @Column(nullable = false, length = 120)
    private String name;

    @Lob
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Column(name = "billing_cycle", nullable = false, length = 32)
    private String billingCycle;

    @Column(name = "stripe_price_id", length = 255)
    private String stripePriceId;

    @Column(columnDefinition = "JSON")
    private String features;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @OneToMany(mappedBy = "subscriptionPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Subscriber> subscribers = new ArrayList<>();
}
