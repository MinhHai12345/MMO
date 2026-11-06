package com.mmo.module.fb.ingest.adapter.out.persistence.identity;

import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.entity.MatchAdvancedStats;
import com.mmo.module.fb.domain.match.enums.MatchEnrichmentResource;
import com.mmo.module.fb.domain.match.enums.MatchEnrichmentStatus;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.match.enums.MatchStatus;
import com.mmo.module.fb.domain.match.repository.MatchAdvancedStatsRepository;
import com.mmo.module.fb.domain.match.repository.MatchExternalIdRepository;
import com.mmo.module.fb.domain.match.repository.MatchRepository;
import com.mmo.module.fb.ingest.application.port.out.SofaScoreMatchEnrichmentQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** JPA adapter for bounded SofaScore enrichment candidate and mapping queries. */
@Repository
@RequiredArgsConstructor
public class SofaScoreMatchEnrichmentQueryJpaAdapter implements SofaScoreMatchEnrichmentQueryPort {
    private final MatchRepository matchRepository;
    private final MatchExternalIdRepository matchExternalIdRepository;
    private final MatchAdvancedStatsRepository matchAdvancedStatsRepository;

    @Override
    public List<Match> findMatchesMissingAdvancedStatistics(Instant retryBefore, int limit) {
        return matchRepository.findFinishedMatchesMissingAdvancedStats(
                MatchStatus.FINISHED, MatchProvider.SOFA_SCORE, MatchEnrichmentResource.TEAM_STATISTICS,
                MatchEnrichmentStatus.SUCCEEDED, MatchEnrichmentStatus.IN_PROGRESS,
                retryBefore, PageRequest.of(0, limit));
    }

    @Override
    public List<Match> findMatchesMissingPlayerStatistics(Instant retryBefore, int limit) {
        return matchRepository.findFinishedMatchesMissingPlayerStatistics(
                MatchStatus.FINISHED, MatchProvider.SOFA_SCORE, MatchEnrichmentResource.PLAYER_STATISTICS,
                MatchEnrichmentStatus.SUCCEEDED, MatchEnrichmentStatus.IN_PROGRESS,
                retryBefore, PageRequest.of(0, limit));
    }

    @Override
    public Optional<String> findProviderMatchId(Long matchId, MatchProvider provider) {
        return matchExternalIdRepository.findByMatchIdAndProvider(matchId, provider)
                .map(mapping -> mapping.getExternalId());
    }

    @Override
    public Optional<MatchAdvancedStats> findTeamAdvancedStats(Long matchId, Long teamId) {
        return matchAdvancedStatsRepository.findByMatchIdAndTeamId(matchId, teamId);
    }

    @Override
    public void saveAdvancedStatistics(List<MatchAdvancedStats> statistics) {
        if (statistics != null && !statistics.isEmpty()) {
            matchAdvancedStatsRepository.saveAll(statistics);
        }
    }
}
