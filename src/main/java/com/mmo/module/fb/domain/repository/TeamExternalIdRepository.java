package com.mmo.module.fb.domain.repository;

import com.mmo.module.fb.domain.entity.TeamExternalId;
import com.mmo.module.fb.domain.enums.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TeamExternalIdRepository extends JpaRepository<TeamExternalId, Long> {
    Optional<TeamExternalId> findByTeamIdAndProvider(Long teamId, Provider provider);

    Optional<TeamExternalId> findByProviderAndExternalId(Provider provider, String externalId);
}
