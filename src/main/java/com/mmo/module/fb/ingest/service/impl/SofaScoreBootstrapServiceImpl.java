package com.mmo.module.fb.ingest.service.impl;

import com.mmo.module.fb.ingest.entity.CrawlerHealth;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.repository.CrawlerHealthRepository;
import com.mmo.module.fb.ingest.service.FixtureIngestionService;
import com.mmo.module.fb.ingest.service.LeagueIngestionService;
import com.mmo.module.fb.ingest.service.SofaScoreBootstrapService;
import com.mmo.module.fb.ingest.service.TeamIngestionService;
import com.mmo.module.fb.league.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.league.repository.SeasonExternalIdRepository;
import com.mmo.module.fb.match.repository.MatchExternalIdRepository;
import com.mmo.module.fb.team.repository.TeamExternalIdRepository;
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
public class SofaScoreBootstrapServiceImpl implements SofaScoreBootstrapService {

    private static final Provider PROVIDER = Provider.SOFA_SCORE;
    private static final String BOOTSTRAP_STATE_NAME = "SOFA_SCORE_CORE_BOOTSTRAP";
    private static final String RUNNING = "RUNNING";
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED = "FAILED";

    private final LeagueIngestionService leagueIngestionService;
    private final TeamIngestionService teamIngestionService;
    private final FixtureIngestionService fixtureIngestionService;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final SeasonExternalIdRepository seasonExternalIdRepository;
    private final TeamExternalIdRepository teamExternalIdRepository;
    private final MatchExternalIdRepository matchExternalIdRepository;
    private final CrawlerHealthRepository crawlerHealthRepository;

    @Override
    public void syncInitialDataIfRequired() {
        if (!isBootstrapRequired()) {
            log.info("SofaScore core-data bootstrap is already complete; skipping initial import");
            return;
        }

        CrawlerHealth state = crawlerHealthRepository.findByCrawlerName(BOOTSTRAP_STATE_NAME)
                .orElseGet(() -> CrawlerHealth.builder()
                        .crawlerName(BOOTSTRAP_STATE_NAME)
                        .status(RUNNING)
                        .errorCount(0)
                        .build());
        state.setStatus(RUNNING);
        state.setLastRun(Instant.now());
        state.setLastErrorMessage(null);
        crawlerHealthRepository.save(state);

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

            state.setStatus(SUCCESS);
            state.setLastSuccess(Instant.now());
            state.setLastErrorMessage(null);
            crawlerHealthRepository.save(state);

            log.info("SofaScore cold-start import completed. leagues={}, seasons={}, teams={}, current-season fixtures={}",
                    leagueExternalIdRepository.findAllByProvider(PROVIDER).size(),
                    seasonExternalIdRepository.findAllByProvider(PROVIDER).size(),
                    teamExternalIdRepository.findAllByProvider(PROVIDER).size(),
                    currentSeasonFixtures);
        } catch (RuntimeException exception) {
            state.setStatus(FAILED);
            state.setErrorCount((state.getErrorCount() == null ? 0 : state.getErrorCount()) + 1);
            state.setLastErrorMessage(exception.getMessage());
            crawlerHealthRepository.save(state);
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

        CrawlerHealth state = crawlerHealthRepository.findByCrawlerName(BOOTSTRAP_STATE_NAME).orElse(null);
        return state == null || !SUCCESS.equalsIgnoreCase(state.getStatus())
                || hasCurrentSeasonWithoutSofaScoreFixtures();
    }

    private boolean hasCurrentSeasonWithoutSofaScoreFixtures() {
        Set<Long> currentSeasonIds = new HashSet<>(
                seasonExternalIdRepository.findCurrentSeasonIdsByProvider(PROVIDER));
        if (currentSeasonIds.isEmpty()) {
            return true;
        }
        Set<Long> mappedSeasonIds = new HashSet<>(
                matchExternalIdRepository.findMappedCurrentSeasonIdsByProvider(PROVIDER));
        return !mappedSeasonIds.containsAll(currentSeasonIds);
    }

    private long countCurrentSeasonFixtures() {
        return matchExternalIdRepository.countCurrentSeasonByProvider(PROVIDER);
    }

    private void requireMappings(String resource, long count) {
        if (count <= 0) {
            throw new IllegalStateException("SofaScore bootstrap did not persist any " + resource
                    + " mappings. Check SofaScore API responses, configured tournament IDs, and crawler logs.");
        }
    }
}
