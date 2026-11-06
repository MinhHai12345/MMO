package com.mmo.module.fb.ingest.adapter.out.persistence.identity;

import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.entity.MatchExternalId;
import com.mmo.module.fb.domain.match.enums.MatchEnrichmentResource;
import com.mmo.module.fb.domain.match.enums.MatchEnrichmentStatus;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.match.enums.MatchStatus;
import com.mmo.module.fb.domain.match.repository.MatchExternalIdRepository;
import com.mmo.module.fb.domain.match.repository.MatchRepository;
import com.mmo.module.fb.ingest.application.port.out.UpcomingOddsMatchQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/** JPA adapter for the bounded upcoming-odds match queries. */
@Repository
@RequiredArgsConstructor
public class UpcomingOddsMatchQueryJpaAdapter implements UpcomingOddsMatchQueryPort {
    private final MatchRepository matchRepository;
    private final MatchExternalIdRepository matchExternalIdRepository;

    @Override
    public List<Match> findScheduledMatches(Instant fromInclusive, Instant toInclusive) {
        return matchRepository.findByStatusAndMatchTimeBetweenOrderByMatchTimeAsc(
                MatchStatus.SCHEDULED, fromInclusive, toInclusive);
    }

    @Override
    public List<Match> findEligibleUpcomingMatches(
            MatchStatus status, Instant fromInclusive, Instant toInclusive,
            MatchProvider provider, MatchEnrichmentResource resource,
            MatchEnrichmentStatus succeeded, MatchEnrichmentStatus inProgress,
            Instant retryBefore, int limit) {
        return matchRepository.findUpcomingWithProviderMapping(status, fromInclusive, toInclusive,
                provider, resource, succeeded, inProgress, retryBefore, PageRequest.of(0, limit));
    }

    @Override
    public List<FixtureMapping> findFixtureMappings(MatchProvider provider) {
        return matchExternalIdRepository.findAllByProvider(provider).stream()
                .filter(mapping -> mapping.getMatch() != null && mapping.getMatch().getId() != null)
                .map(mapping -> new FixtureMapping(mapping.getMatch().getId(), mapping.getExternalId()))
                .toList();
    }
}
