package com.mmo.module.fb.ingest.service.impl;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.sofascore.SofaScoreLineupsClient;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreLineupsResponseDTO;
import com.mmo.module.fb.match.entity.Match;
import com.mmo.module.fb.match.entity.MatchExternalId;
import com.mmo.module.fb.match.enums.MatchStatus;
import com.mmo.module.fb.match.repository.MatchExternalIdRepository;
import com.mmo.module.fb.match.repository.MatchRepository;
import com.mmo.module.fb.player.repository.PlayerMatchStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SofaScorePlayerStatisticsIngestionService {
    private static final int MATCH_BATCH_SIZE = 20;

    private final MatchRepository matchRepository;
    private final MatchExternalIdRepository matchExternalIdRepository;
    private final PlayerMatchStatsRepository playerMatchStatsRepository;
    private final SofaScoreLineupsClient lineupsClient;
    private final SofaScorePlayerStatsPersistenceService persistenceService;

    public void syncFinishedMatchPlayerStatistics() {
        Instant retryBefore = Instant.now().minus(24, ChronoUnit.HOURS);
        List<Match> candidates = matchRepository.findByStatus(MatchStatus.FINISHED).stream()
                .filter(match -> match.getHomeScore() != null && match.getAwayScore() != null)
                .filter(match -> match.getPlayerStatisticsLastAttemptAt() == null
                        || match.getPlayerStatisticsLastAttemptAt().isBefore(retryBefore))
                .sorted(Comparator.comparing(Match::getMatchTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .filter(match -> match.getId() != null)
                .filter(match -> !hasPlayerStats(match.getId()))
                .filter(match -> matchExternalIdRepository.findByMatchIdAndProvider(match.getId(), Provider.SOFA_SCORE).isPresent())
                .limit(MATCH_BATCH_SIZE).toList();

        int persisted = 0;
        int skipped = 0;
        for (Match match : candidates) {
            MatchExternalId externalId = matchExternalIdRepository
                    .findByMatchIdAndProvider(match.getId(), Provider.SOFA_SCORE).orElse(null);
            if (externalId == null) {
                skipped++;
                continue;
            }
            try {
                SofaScoreLineupsResponseDTO response =
                        lineupsClient.fetchLineups(Long.valueOf(externalId.getExternalId()));
                if (response == null || (empty(response.home()) && empty(response.away()))) {
                    persistenceService.markAttempt(match.getId());
                    skipped++;
                    continue;
                }
                int rows = persistenceService.persist(match.getId(), response);
                persistenceService.markAttempt(match.getId());
                if (rows > 0) persisted++; else skipped++;
            } catch (Exception exception) {
                persistenceService.markAttempt(match.getId());
                log.warn("Player statistics sync failed for matchId={} providerMatchId={}: {}",
                        match.getId(), externalId.getExternalId(), exception.getMessage());
            }
        }
        log.info("SofaScore player-statistics sync finished. candidates={}, persistedMatches={}, skipped={}",
                candidates.size(), persisted, skipped);
    }

    private boolean hasPlayerStats(Long matchId) {
        return playerMatchStatsRepository.countByMatchId(matchId) > 0;
    }

    private boolean empty(SofaScoreLineupsResponseDTO.LineupTeamDTO team) {
        return team == null || team.players() == null || team.players().isEmpty();
    }
}
