package com.mmo.module.fb.domain.repository;

import com.mmo.module.fb.domain.entity.LeagueExternalId;
import com.mmo.module.fb.domain.enums.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LeagueExternalIdRepository extends JpaRepository<LeagueExternalId, Long> {
    Optional<LeagueExternalId> findByLeagueIdAndProvider(Long leagueId, Provider provider);

    Optional<LeagueExternalId> findByProviderAndExternalId(Provider provider, String externalId);
}
