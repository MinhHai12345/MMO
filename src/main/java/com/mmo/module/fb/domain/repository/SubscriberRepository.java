package com.mmo.module.fb.domain.repository;

import com.mmo.module.fb.domain.entity.Subscriber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriberRepository extends JpaRepository<Subscriber, Long> {
    Optional<Subscriber> findByUserId(String userId);
    List<Subscriber> findBySubscriptionStatus(String status);
}
