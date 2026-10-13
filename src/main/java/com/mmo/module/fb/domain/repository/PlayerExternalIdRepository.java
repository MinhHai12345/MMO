package com.mmo.module.fb.domain.repository;

import com.mmo.module.fb.domain.entity.Player;
import com.mmo.module.fb.domain.entity.PlayerExternalId;
import com.mmo.module.fb.domain.enums.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlayerExternalIdRepository extends JpaRepository<PlayerExternalId, Long> {
    Optional<PlayerExternalId> findByPlayerIdAndProvider(Long playerId, Provider provider);
    Optional<PlayerExternalId> findByProviderAndExternalId(Provider provider, String externalId);
}
