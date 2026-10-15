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
@Table(name = "fb_affiliate_tracking", uniqueConstraints = @UniqueConstraint(columnNames = {"affiliate_code"}))
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AffiliateTracking extends AbstractEntity {

    @Column(name = "affiliate_code", nullable = false, unique = true)
    private String affiliateCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscriber_id")
    private Subscriber subscriber;

    @Column(name = "click_count")
    @Builder.Default
    private Integer clickCount = 0;

    @Column(name = "conversion_count")
    @Builder.Default
    private Integer conversionCount = 0;

    @Column(name = "commission_rate")
    private BigDecimal commissionRate;

    @Column(name = "total_commission")
    @Builder.Default
    private BigDecimal totalCommission = BigDecimal.ZERO;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
}
