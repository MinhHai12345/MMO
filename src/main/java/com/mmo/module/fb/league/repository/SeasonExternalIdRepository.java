package com.mmo.module.fb.league.repository;

import com.mmo.module.fb.league.entity.Season;
import com.mmo.module.fb.league.entity.SeasonExternalId;
import com.mmo.module.fb.ingest.enums.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeasonExternalIdRepository extends JpaRepository<SeasonExternalId, Long> {
    @EntityGraph(attributePaths = {"season", "season.league"})
    List<SeasonExternalId> findAllByProvider(Provider provider);

    @Query("SELECT se.season.id FROM SeasonExternalId se WHERE se.provider = :provider AND se.season.isCurrent = true")
    List<Long> findCurrentSeasonIdsByProvider(@Param("provider") Provider provider);
    @EntityGraph(attributePaths = {"season", "season.league"})
    Optional<SeasonExternalId> findBySeasonIdAndProvider(Long seasonId, Provider provider);
    @EntityGraph(attributePaths = {"season", "season.league"})
    Optional<SeasonExternalId> findByProviderAndExternalId(Provider provider, String externalId);
}
