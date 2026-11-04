package com.mmo.module.fb.ingest.service.impl;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.application.port.out.PlayerStatisticsIngestionPort;
import com.mmo.module.fb.ingest.provider.sofascore.client.SofaScoreLineupsClient;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreLineupsResponseDTO;
import com.mmo.module.fb.ingest.persistence.sofascore.SofaScorePlayerStatsPersistenceService;
import com.mmo.module.fb.match.entity.Match;
import com.mmo.module.fb.match.entity.MatchExternalId;
import com.mmo.module.fb.match.enums.MatchStatus;
import com.mmo.module.fb.match.repository.MatchExternalIdRepository;
import com.mmo.module.fb.match.repository.MatchRepository;
import com.mmo.module.fb.match.enums.MatchEnrichmentResource;
import com.mmo.module.fb.match.enums.MatchProvider;
import com.mmo.module.fb.match.enums.MatchEnrichmentStatus;
import com.mmo.module.fb.match.service.MatchEnrichmentStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SofaScorePlayerStatisticsIngestionService implements PlayerStatisticsIngestionPort {
    private static final int MATCH_BATCH_SIZE = 20;

    private final MatchRepository matchRepository;
    private final MatchExternalIdRepository matchExternalIdRepository;
    private final SofaScoreLineupsClient lineupsClient;
    private final SofaScorePlayerStatsPersistenceService persistenceService;
    private final MatchEnrichmentStateService enrichmentStateService;

    public void syncFinishedMatchPlayerStatistics() {
        Instant now = Instant.now();
        Instant retryBefore = now.minus(24, ChronoUnit.HOURS);
        List<Match> candidates = matchRepository.findFinishedMatchesMissingPlayerStatistics(
                MatchStatus.FINISHED, MatchProvider.SOFA_SCORE, MatchEnrichmentResource.PLAYER_STATISTICS,
                MatchEnrichmentStatus.SUCCEEDED, MatchEnrichmentStatus.IN_PROGRESS,
                retryBefore, PageRequest.of(0, MATCH_BATCH_SIZE));

        int persisted = 0;
        int skipped = 0;
        for (Match match : candidates) {
            enrichmentStateService.start(match, MatchProvider.SOFA_SCORE, MatchEnrichmentResource.PLAYER_STATISTICS);
            MatchExternalId externalId = matchExternalIdRepository
                    .findByMatchIdAndProvider(match.getId(), MatchProvider.SOFA_SCORE).orElse(null);
            if (externalId == null) {
                enrichmentStateService.skipped(match, MatchProvider.SOFA_SCORE, MatchEnrichmentResource.PLAYER_STATISTICS);
                skipped++;
                continue;
            }
            try {
                SofaScoreLineupsResponseDTO response =
                        lineupsClient.fetchLineups(Long.valueOf(externalId.getExternalId()));
                if (response == null || (empty(response.home()) && empty(response.away()))) {
                    enrichmentStateService.empty(match, MatchProvider.SOFA_SCORE, MatchEnrichmentResource.PLAYER_STATISTICS);
                    skipped++;
                    continue;
                }
                int rows = persistenceService.persist(match.getId(), response);
                if (rows > 0) {
                    enrichmentStateService.succeed(match, MatchProvider.SOFA_SCORE, MatchEnrichmentResource.PLAYER_STATISTICS);
                    persisted++;
                } else {
                    enrichmentStateService.empty(match, MatchProvider.SOFA_SCORE, MatchEnrichmentResource.PLAYER_STATISTICS);
                    skipped++;
                }
            } catch (Exception exception) {
                enrichmentStateService.failed(match, MatchProvider.SOFA_SCORE, MatchEnrichmentResource.PLAYER_STATISTICS, exception);
                log.warn("Player statistics sync failed for matchId={} providerMatchId={}: {}",
                        match.getId(), externalId.getExternalId(), exception.getMessage());
            }
        }
        log.info("SofaScore player-statistics sync finished. candidates={}, persistedMatches={}, skipped={}",
                candidates.size(), persisted, skipped);
    }

    private boolean empty(SofaScoreLineupsResponseDTO.LineupTeamDTO team) {
        return team == null || team.players() == null || team.players().isEmpty();
    }
}
