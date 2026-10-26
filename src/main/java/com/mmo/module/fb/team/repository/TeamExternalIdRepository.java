package com.mmo.module.fb.team.repository;

import com.mmo.module.fb.team.entity.TeamExternalId;
import com.mmo.module.fb.ingest.enums.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamExternalIdRepository extends JpaRepository<TeamExternalId, Long> {
    List<TeamExternalId> findAllByProvider(Provider provider);
    @EntityGraph(attributePaths = {"team"})
    Optional<TeamExternalId> findByTeamIdAndProvider(Long teamId, Provider provider);

    @EntityGraph(attributePaths = {"team"})
    Optional<TeamExternalId> findByProviderAndExternalId(Provider provider, String externalId);
}
