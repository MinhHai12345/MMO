package com.mmo.module.fb.league.repository;

import com.mmo.module.fb.league.entity.LeagueExternalId;
import com.mmo.module.fb.ingest.enums.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeagueExternalIdRepository extends JpaRepository<LeagueExternalId, Long> {
    @EntityGraph(attributePaths = {"league"})
    List<LeagueExternalId> findAllByProvider(Provider provider);
    @EntityGraph(attributePaths = {"league"})
    Optional<LeagueExternalId> findByLeagueIdAndProvider(Long leagueId, Provider provider);

    @EntityGraph(attributePaths = {"league"})
    Optional<LeagueExternalId> findByProviderAndExternalId(Provider provider, String externalId);
}
