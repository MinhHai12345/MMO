package com.mmo.module.fb.ingest.application.service.integration;

import com.mmo.module.fb.ingest.application.model.provider.ProviderLeague;
import com.mmo.module.fb.ingest.application.port.in.SofaScoreLeagueLookupUseCase;
import com.mmo.module.fb.ingest.application.port.out.provider.LeagueIngestionPort;
import com.mmo.module.fb.ingest.enums.Provider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Fetches one SofaScore tournament and its seasons without running other ingestion stages.
 * This operation is read-only and does not persist provider data.
 */
@Slf4j
@Service
public class SofaScoreLeagueLookupService implements SofaScoreLeagueLookupUseCase {

    private final LeagueIngestionPort leagueProvider;

    public SofaScoreLeagueLookupService(List<LeagueIngestionPort> providers) {
        this.leagueProvider = providers.stream()
                .filter(Objects::nonNull)
                .filter(provider -> provider.getProvider() == Provider.SOFA_SCORE)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("SofaScore league provider is not configured"));
    }

    @Override
    public List<ProviderLeague> fetchLeague(Long tournamentId) {
        if (tournamentId == null || tournamentId <= 0) {
            throw new IllegalArgumentException("tournamentId must be positive");
        }

        long startedAt = System.nanoTime();
        log.info("[SOFA_SCORE][LEAGUE_LOOKUP] Starting tournament lookup: tournamentId={}", tournamentId);
        try {
            List<ProviderLeague> leagues = leagueProvider.fetchLeagues(List.of(tournamentId));
            List<ProviderLeague> matchingLeagues = leagues == null ? List.of() : leagues.stream()
                    .filter(Objects::nonNull)
                    .filter(league -> String.valueOf(tournamentId).equals(league.externalId()))
                    .toList();

            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("[SOFA_SCORE][LEAGUE_LOOKUP] Completed: tournamentId={}, leagues={}, durationMs={}",
                    tournamentId, matchingLeagues.size(), durationMs);
            return matchingLeagues;
        } catch (RuntimeException exception) {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.error("[SOFA_SCORE][LEAGUE_LOOKUP] Failed: tournamentId={}, durationMs={}, reason={}",
                    tournamentId, durationMs, exception.getMessage(), exception);
            throw exception;
        }
    }
}
