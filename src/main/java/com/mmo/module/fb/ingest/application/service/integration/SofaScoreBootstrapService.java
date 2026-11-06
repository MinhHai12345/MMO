package com.mmo.module.fb.ingest.application.service.integration;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.application.port.out.CrawlerHealthStatePort;
import com.mmo.module.fb.ingest.application.port.out.SofaScoreMappingStatePort;
import com.mmo.module.fb.ingest.application.port.in.FixtureIngestionUseCase;
import com.mmo.module.fb.ingest.application.port.in.LeagueIngestionUseCase;
import com.mmo.module.fb.ingest.application.port.in.SofaScoreBootstrapUseCase;
import com.mmo.module.fb.ingest.application.port.in.TeamIngestionUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Cold-start coordinator for SofaScore. Domain persistence remains in the shared ingestion
 * services, so provider IDs are registered through ExternalIdMappingService and all sources
 * continue to share canonical football entities.
 *
 * <p>The existing crawler-health record also acts as a durable bootstrap checkpoint. Failed
 * imports remain retryable; the job never marks the bootstrap successful until each stage
 * has produced provider mappings.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SofaScoreBootstrapService implements SofaScoreBootstrapUseCase {

    private static final Provider PROVIDER = Provider.SOFA_SCORE;
    private static final String BOOTSTRAP_STATE_NAME = "SOFA_SCORE_CORE_BOOTSTRAP";

    private final LeagueIngestionUseCase leagueIngestionService;
    private final TeamIngestionUseCase teamIngestionService;
    private final FixtureIngestionUseCase fixtureIngestionService;
    private final SofaScoreMappingStatePort mappingStatePort;
    private final CrawlerHealthStatePort crawlerHealthStatePort;

    @Override
    public void syncInitialDataIfRequired() {
        if (!isBootstrapRequired()) {
            log.info("SofaScore core-data bootstrap is already complete; skipping initial import");
            return;
        }

        crawlerHealthStatePort.markRunning(BOOTSTRAP_STATE_NAME, Instant.now());

        try {
            log.info("Starting SofaScore cold-start import: leagues/seasons -> current-season teams -> fixtures");
            leagueIngestionService.syncLeaguesForProvider(PROVIDER);
            requireMappings("league", mappingStatePort.countLeagues());
            requireMappings("season", mappingStatePort.countSeasons());

            teamIngestionService.syncTeamsForProvider(PROVIDER);
            requireMappings("team", mappingStatePort.countTeams());

            // Null date bounds intentionally import the complete current season, not just the
            // next 14 days, so finished matches become eligible for statistics/xG ingestion.
            fixtureIngestionService.syncFixturesForProvider(PROVIDER, null, null);
            long currentSeasonFixtures = countCurrentSeasonFixtures();
            requireMappings("current-season fixture", currentSeasonFixtures);

            crawlerHealthStatePort.markSucceeded(BOOTSTRAP_STATE_NAME, Instant.now());

            log.info("SofaScore cold-start import completed. leagues={}, seasons={}, teams={}, current-season fixtures={}",
                    mappingStatePort.countLeagues(),
                    mappingStatePort.countSeasons(),
                    mappingStatePort.countTeams(),
                    currentSeasonFixtures);
        } catch (RuntimeException exception) {
            crawlerHealthStatePort.markFailed(BOOTSTRAP_STATE_NAME, Instant.now(), exception.getMessage());
            log.error("SofaScore bootstrap failed and remains retryable: {}", exception.getMessage(), exception);
            throw exception;
        }
    }

    private boolean isBootstrapRequired() {
        if (mappingStatePort.countLeagues() == 0
                || mappingStatePort.countSeasons() == 0
                || mappingStatePort.countTeams() == 0
                || countCurrentSeasonFixtures() == 0) {
            return true;
        }

        return !crawlerHealthStatePort.isSuccessful(BOOTSTRAP_STATE_NAME)
                || hasCurrentSeasonWithoutSofaScoreFixtures();
    }

    private boolean hasCurrentSeasonWithoutSofaScoreFixtures() {
        Set<Long> currentSeasonIds = new HashSet<>(
                mappingStatePort.currentSeasonIds());
        if (currentSeasonIds.isEmpty()) {
            return true;
        }
        Set<Long> mappedSeasonIds = new HashSet<>(
                mappingStatePort.mappedCurrentSeasonIds());
        return !mappedSeasonIds.containsAll(currentSeasonIds);
    }

    private long countCurrentSeasonFixtures() {
        return mappingStatePort.countCurrentSeasonFixtures();
    }

    private void requireMappings(String resource, long count) {
        if (count <= 0) {
            throw new IllegalStateException("SofaScore bootstrap did not persist any " + resource
                    + " mappings. Check SofaScore API responses, configured tournament IDs, and crawler logs.");
        }
    }
}
