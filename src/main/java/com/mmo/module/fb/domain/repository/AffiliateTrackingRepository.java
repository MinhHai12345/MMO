package com.mmo.module.fb.domain.repository;

import com.mmo.module.fb.domain.entity.AffiliateTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AffiliateTrackingRepository extends JpaRepository<AffiliateTracking, Long> {
}
