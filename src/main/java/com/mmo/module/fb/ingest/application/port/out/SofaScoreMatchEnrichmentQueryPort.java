package com.mmo.module.fb.ingest.application.port.out;

import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.entity.MatchAdvancedStats;
import com.mmo.module.fb.domain.match.enums.MatchProvider;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence queries required by SofaScore match and player statistics ingestion. */
public interface SofaScoreMatchEnrichmentQueryPort {
    List<Match> findMatchesMissingAdvancedStatistics(Instant retryBefore, int limit);
    List<Match> findMatchesMissingPlayerStatistics(Instant retryBefore, int limit);
    Optional<String> findProviderMatchId(Long matchId, MatchProvider provider);
    Optional<MatchAdvancedStats> findTeamAdvancedStats(Long matchId, Long teamId);
    void saveAdvancedStatistics(List<MatchAdvancedStats> statistics);
}
