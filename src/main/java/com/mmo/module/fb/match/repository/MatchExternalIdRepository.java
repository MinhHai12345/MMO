package com.mmo.module.fb.match.repository;

import com.mmo.module.fb.match.entity.Match;
import com.mmo.module.fb.match.entity.MatchExternalId;
import com.mmo.module.fb.ingest.enums.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatchExternalIdRepository extends JpaRepository<MatchExternalId, Long> {
    @EntityGraph(attributePaths = {"match", "match.season"})
    List<MatchExternalId> findAllByProvider(Provider provider);

    @Query("SELECT COUNT(externalId.id) FROM MatchExternalId externalId WHERE externalId.provider = :provider AND externalId.match.season.isCurrent = true")
    long countCurrentSeasonByProvider(@Param("provider") Provider provider);

    @Query("SELECT DISTINCT externalId.match.season.id FROM MatchExternalId externalId WHERE externalId.provider = :provider AND externalId.match.season.isCurrent = true")
    List<Long> findMappedCurrentSeasonIdsByProvider(@Param("provider") Provider provider);
    @EntityGraph(attributePaths = {"match", "match.season", "match.homeTeam", "match.awayTeam"})
    Optional<MatchExternalId> findByMatchIdAndProvider(Long matchId, Provider provider);
    @EntityGraph(attributePaths = {"match", "match.season", "match.homeTeam", "match.awayTeam"})
    Optional<MatchExternalId> findByProviderAndExternalId(Provider provider, String externalId);
}
