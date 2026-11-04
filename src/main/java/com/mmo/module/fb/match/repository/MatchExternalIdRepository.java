package com.mmo.module.fb.match.repository;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.match.entity.MatchExternalId;
import com.mmo.module.fb.match.enums.MatchProvider;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Repository API is domain-provider based; overloads preserve the ingestion adapter contract. */
public interface MatchExternalIdRepository extends JpaRepository<MatchExternalId, Long> {

    @EntityGraph(attributePaths = {"match", "match.season"})
    List<MatchExternalId> findAllByProvider(MatchProvider provider);

    @Query("""
            SELECT COUNT(externalId.id)
            FROM MatchExternalId externalId
            WHERE externalId.provider = :provider
              AND externalId.match.season.isCurrent = true
            """)
    long countCurrentSeasonByProvider(@Param("provider") MatchProvider provider);

    @Query("""
            SELECT DISTINCT externalId.match.season.id
            FROM MatchExternalId externalId
            WHERE externalId.provider = :provider
              AND externalId.match.season.isCurrent = true
            """)
    List<Long> findMappedCurrentSeasonIdsByProvider(@Param("provider") MatchProvider provider);

    @EntityGraph(attributePaths = {"match", "match.season", "match.homeTeam", "match.awayTeam"})
    Optional<MatchExternalId> findByMatchIdAndProvider(Long matchId, MatchProvider provider);

    @EntityGraph(attributePaths = {"match", "match.season", "match.homeTeam", "match.awayTeam"})
    Optional<MatchExternalId> findByProviderAndExternalId(MatchProvider provider, String externalId);

    boolean existsByMatchIdAndProvider(Long matchId, MatchProvider provider);

    boolean existsByProviderAndExternalId(MatchProvider provider, String externalId);

    default List<MatchExternalId> findAllByProvider(Provider provider) {
        return findAllByProvider(MatchProvider.from(provider));
    }

    default long countCurrentSeasonByProvider(Provider provider) {
        return countCurrentSeasonByProvider(MatchProvider.from(provider));
    }

    default List<Long> findMappedCurrentSeasonIdsByProvider(Provider provider) {
        return findMappedCurrentSeasonIdsByProvider(MatchProvider.from(provider));
    }

    default Optional<MatchExternalId> findByMatchIdAndProvider(Long matchId, Provider provider) {
        return findByMatchIdAndProvider(matchId, MatchProvider.from(provider));
    }

    default Optional<MatchExternalId> findByProviderAndExternalId(Provider provider, String externalId) {
        return findByProviderAndExternalId(MatchProvider.from(provider), externalId);
    }

    default boolean existsByMatchIdAndProvider(Long matchId, Provider provider) {
        return existsByMatchIdAndProvider(matchId, MatchProvider.from(provider));
    }

    default boolean existsByProviderAndExternalId(Provider provider, String externalId) {
        return existsByProviderAndExternalId(MatchProvider.from(provider), externalId);
    }
}
