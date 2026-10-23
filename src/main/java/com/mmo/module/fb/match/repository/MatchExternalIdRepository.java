package com.mmo.module.fb.match.repository;

import com.mmo.module.fb.match.entity.Match;
import com.mmo.module.fb.match.entity.MatchExternalId;
import com.mmo.module.fb.ingest.enums.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatchExternalIdRepository extends JpaRepository<MatchExternalId, Long> {
    List<MatchExternalId> findAllByProvider(Provider provider);
    Optional<MatchExternalId> findByMatchIdAndProvider(Long matchId, Provider provider);
    Optional<MatchExternalId> findByProviderAndExternalId(Provider provider, String externalId);
}
