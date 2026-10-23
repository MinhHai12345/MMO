package com.mmo.module.fb.monetization.repository;

import com.mmo.module.fb.monetization.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {
    List<SubscriptionPlan> findByIsActiveTrue();
}
