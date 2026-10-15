package com.mmo.module.fb.domain.entity;

import com.mmo.core.entity.AbstractEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.Lob;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "fb_subscription_plans")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubscriptionPlan extends AbstractEntity {

    @Column(nullable = false)
    private String name;

    @Lob
    private String description;

    @Column(nullable = false)
    private String price;

    @Builder.Default
    private String currency = "USD";

    @Column(name = "billing_cycle", nullable = false)
    private String billingCycle;

    @Column(name = "stripe_price_id")
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
