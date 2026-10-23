package com.mmo.module.fb.league.repository;

import com.mmo.module.fb.league.entity.LeagueExternalId;
import com.mmo.module.fb.ingest.enums.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeagueExternalIdRepository extends JpaRepository<LeagueExternalId, Long> {
    List<LeagueExternalId> findAllByProvider(Provider provider);
    Optional<LeagueExternalId> findByLeagueIdAndProvider(Long leagueId, Provider provider);

    Optional<LeagueExternalId> findByProviderAndExternalId(Provider provider, String externalId);
}
