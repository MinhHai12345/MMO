package com.mmo.module.fb.ingest.application.service.integration;

import com.mmo.module.fb.ingest.application.port.out.PlayerStatisticsIngestionPort;
import com.mmo.module.fb.ingest.application.port.out.provider.PlayerLineupsIngestionPort;
import com.mmo.module.fb.ingest.provider.model.ProviderLineups;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.application.registry.ProviderIngestionRegistry;

import com.mmo.module.fb.ingest.application.port.out.PlayerMatchStatisticsPersistencePort;
import com.mmo.module.fb.ingest.application.port.out.SofaScoreMatchEnrichmentQueryPort;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.enums.MatchStatus;
import com.mmo.module.fb.domain.match.enums.MatchEnrichmentResource;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.match.enums.MatchEnrichmentStatus;
import com.mmo.module.fb.domain.match.service.MatchEnrichmentStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SofaScorePlayerStatisticsIngestionService implements PlayerStatisticsIngestionPort {
    private static final int MATCH_BATCH_SIZE = 20;

    private final SofaScoreMatchEnrichmentQueryPort matchEnrichmentQueryPort;
    private final ProviderIngestionRegistry providerRegistry;
    private final PlayerMatchStatisticsPersistencePort persistenceService;
    private final MatchEnrichmentStateService enrichmentStateService;

    public void syncFinishedMatchPlayerStatistics() {
        Instant now = Instant.now();
        Instant retryBefore = now.minus(24, ChronoUnit.HOURS);
        List<Match> candidates = matchEnrichmentQueryPort.findMatchesMissingPlayerStatistics(
                retryBefore, MATCH_BATCH_SIZE);

        int persisted = 0;
        int skipped = 0;
        for (Match match : candidates) {
            enrichmentStateService.start(match, MatchProvider.SOFA_SCORE, MatchEnrichmentResource.PLAYER_STATISTICS);
            String externalId = matchEnrichmentQueryPort
                    .findProviderMatchId(match.getId(), MatchProvider.SOFA_SCORE).orElse(null);
            if (externalId == null) {
                enrichmentStateService.skipped(match, MatchProvider.SOFA_SCORE, MatchEnrichmentResource.PLAYER_STATISTICS);
                skipped++;
                continue;
            }
            try {
                PlayerLineupsIngestionPort provider = providerRegistry.playerLineups(Provider.SOFA_SCORE);
                java.util.Optional<ProviderLineups> response = provider.fetchLineups(externalId);
                if (response.isEmpty() || response.get().isEmpty()) {
                    enrichmentStateService.empty(match, MatchProvider.SOFA_SCORE, MatchEnrichmentResource.PLAYER_STATISTICS);
                    skipped++;
                    continue;
                }
                int rows = persistenceService.persist(match.getId(), response.get());
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
                        match.getId(), externalId, exception.getMessage());
            }
        }
        log.info("SofaScore player-statistics sync finished. candidates={}, persistedMatches={}, skipped={}",
                candidates.size(), persisted, skipped);
    }

}
