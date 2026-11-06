package com.mmo.module.fb.ingest.application.port.out;

import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.enums.MatchEnrichmentResource;
import com.mmo.module.fb.domain.match.enums.MatchEnrichmentStatus;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.match.enums.MatchStatus;

import java.time.Instant;
import java.util.List;

/** Query boundary for selecting matches and provider fixture IDs for odds ingestion. */
public interface UpcomingOddsMatchQueryPort {
    List<Match> findScheduledMatches(Instant fromInclusive, Instant toInclusive);

    List<Match> findEligibleUpcomingMatches(MatchStatus status, Instant fromInclusive, Instant toInclusive,
                                            MatchProvider provider, MatchEnrichmentResource resource,
                                            MatchEnrichmentStatus succeeded, MatchEnrichmentStatus inProgress,
                                            Instant retryBefore, int limit);

    List<FixtureMapping> findFixtureMappings(MatchProvider provider);

    record FixtureMapping(Long matchId, String externalId) {}
}
