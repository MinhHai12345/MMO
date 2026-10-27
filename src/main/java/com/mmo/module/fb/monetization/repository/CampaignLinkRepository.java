package com.mmo.module.fb.monetization.repository;

import com.mmo.module.fb.monetization.entity.CampaignLinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CampaignLinkRepository extends JpaRepository<CampaignLinkEntity, Long> {
    Optional<CampaignLinkEntity> findByCampaignCode(String campaignCode);
    Optional<CampaignLinkEntity> findByCampaignCodeAndActiveTrue(String campaignCode);
}
