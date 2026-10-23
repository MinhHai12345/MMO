package com.mmo.module.fb.league.repository;

import com.mmo.module.fb.league.entity.Season;
import com.mmo.module.fb.league.entity.SeasonExternalId;
import com.mmo.module.fb.ingest.enums.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SeasonExternalIdRepository extends JpaRepository<SeasonExternalId, Long> {
    Optional<SeasonExternalId> findBySeasonIdAndProvider(Long seasonId, Provider provider);
    Optional<SeasonExternalId> findByProviderAndExternalId(Provider provider, String externalId);
}
