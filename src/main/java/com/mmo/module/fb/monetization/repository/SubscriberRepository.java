package com.mmo.module.fb.monetization.repository;

import com.mmo.module.fb.monetization.entity.Subscriber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface SubscriberRepository extends JpaRepository<Subscriber, Long> {
    Optional<Subscriber> findByUserId(String userId);
    List<Subscriber> findBySubscriptionStatus(String status);
    List<Subscriber> findBySubscriptionStatusAndIdGreaterThanOrderByIdAsc(
            String status, Long afterId, Pageable pageable);
}
