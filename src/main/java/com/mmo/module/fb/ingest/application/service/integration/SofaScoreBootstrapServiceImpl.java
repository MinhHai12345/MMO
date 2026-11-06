package com.mmo.module.fb.ingest.application.service.integration;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.application.port.out.CrawlerHealthStatePort;
import com.mmo.module.fb.ingest.application.port.in.FixtureIngestionUseCase;
import com.mmo.module.fb.ingest.application.port.in.LeagueIngestionUseCase;
import com.mmo.module.fb.ingest.application.port.in.SofaScoreBootstrapUseCase;
import com.mmo.module.fb.ingest.application.port.in.TeamIngestionUseCase;
import com.mmo.module.fb.domain.league.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.league.repository.SeasonExternalIdRepository;
import com.mmo.module.fb.domain.match.repository.MatchExternalIdRepository;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.team.repository.TeamExternalIdRepository;
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
public class SofaScoreBootstrapUseCaseImpl implements SofaScoreBootstrapUseCase {

    private static final Provider PROVIDER = Provider.SOFA_SCORE;
    private static final String BOOTSTRAP_STATE_NAME = "SOFA_SCORE_CORE_BOOTSTRAP";

    private final LeagueIngestionUseCase leagueIngestionService;
    private final TeamIngestionUseCase teamIngestionService;
    private final FixtureIngestionUseCase fixtureIngestionService;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final SeasonExternalIdRepository seasonExternalIdRepository;
    private final TeamExternalIdRepository teamExternalIdRepository;
    private final MatchExternalIdRepository matchExternalIdRepository;
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
            requireMappings("league", leagueExternalIdRepository.findAllByProvider(PROVIDER).size());
            requireMappings("season", seasonExternalIdRepository.findAllByProvider(PROVIDER).size());

            teamIngestionService.syncTeamsForProvider(PROVIDER);
            requireMappings("team", teamExternalIdRepository.findAllByProvider(PROVIDER).size());

            // Null date bounds intentionally import the complete current season, not just the
            // next 14 days, so finished matches become eligible for statistics/xG ingestion.
            fixtureIngestionService.syncFixturesForProvider(PROVIDER, null, null);
            long currentSeasonFixtures = countCurrentSeasonFixtures();
            requireMappings("current-season fixture", currentSeasonFixtures);

            crawlerHealthStatePort.markSucceeded(BOOTSTRAP_STATE_NAME, Instant.now());

            log.info("SofaScore cold-start import completed. leagues={}, seasons={}, teams={}, current-season fixtures={}",
                    leagueExternalIdRepository.findAllByProvider(PROVIDER).size(),
                    seasonExternalIdRepository.findAllByProvider(PROVIDER).size(),
                    teamExternalIdRepository.findAllByProvider(PROVIDER).size(),
                    currentSeasonFixtures);
        } catch (RuntimeException exception) {
            crawlerHealthStatePort.markFailed(BOOTSTRAP_STATE_NAME, Instant.now(), exception.getMessage());
            log.error("SofaScore bootstrap failed and remains retryable: {}", exception.getMessage(), exception);
            throw exception;
        }
    }

    private boolean isBootstrapRequired() {
        if (leagueExternalIdRepository.findAllByProvider(PROVIDER).isEmpty()
                || seasonExternalIdRepository.findAllByProvider(PROVIDER).isEmpty()
                || teamExternalIdRepository.findAllByProvider(PROVIDER).isEmpty()
                || countCurrentSeasonFixtures() == 0) {
            return true;
        }

        return !crawlerHealthStatePort.isSuccessful(BOOTSTRAP_STATE_NAME)
                || hasCurrentSeasonWithoutSofaScoreFixtures();
    }

    private boolean hasCurrentSeasonWithoutSofaScoreFixtures() {
        Set<Long> currentSeasonIds = new HashSet<>(
                seasonExternalIdRepository.findCurrentSeasonIdsByProvider(PROVIDER));
        if (currentSeasonIds.isEmpty()) {
            return true;
        }
        Set<Long> mappedSeasonIds = new HashSet<>(
                matchExternalIdRepository.findMappedCurrentSeasonIdsByProvider(MatchProvider.fromName(PROVIDER.name())));
        return !mappedSeasonIds.containsAll(currentSeasonIds);
    }

    private long countCurrentSeasonFixtures() {
        return matchExternalIdRepository.countCurrentSeasonByProvider(MatchProvider.fromName(PROVIDER.name()));
    }

    private void requireMappings(String resource, long count) {
        if (count <= 0) {
            throw new IllegalStateException("SofaScore bootstrap did not persist any " + resource
                    + " mappings. Check SofaScore API responses, configured tournament IDs, and crawler logs.");
        }
    }
}
