package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.ingest.service.FixtureIngestionService;
import com.mmo.module.fb.ingest.service.FootballDataBootstrapService;
import com.mmo.module.fb.ingest.service.LeagueIngestionService;
import com.mmo.module.fb.ingest.service.StandingsIngestionService;
import com.mmo.module.fb.ingest.service.TeamIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Runs the minimum useful data pipeline in dependency order:
 * leagues/seasons -> teams -> fixtures/results -> standings.
 *
 * Network calls and persistence transactions remain inside their respective services.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FootballDataBootstrapServiceImpl implements FootballDataBootstrapService {

    private final AppProperties appProperties;
    private final LeagueIngestionService leagueIngestionService;
    private final TeamIngestionService teamIngestionService;
    private final FixtureIngestionService fixtureIngestionService;
    private final StandingsIngestionService standingsIngestionService;

    @Override
    public void syncCoreData() {
        validateApiFootballConfiguration();

        log.info("Starting API-Football core-data bootstrap");

        leagueIngestionService.syncLeaguesForProvider(Provider.API_FOOTBALL);
        teamIngestionService.syncTeamsForProvider(Provider.API_FOOTBALL);
        // Null date bounds intentionally import all fixtures returned for each current season.
        // Subsequent scheduled fixture/result jobs use narrower windows.
        fixtureIngestionService.syncFixturesForProvider(Provider.API_FOOTBALL, null, null);
        standingsIngestionService.syncStandingsForProvider(Provider.API_FOOTBALL);

        log.info("API-Football core-data bootstrap completed");
    }

    private void validateApiFootballConfiguration() {
        AppProperties.ApiFootball properties = appProperties.getApiFootball();
        if (properties == null || !properties.isEnabled()) {
            throw new IllegalStateException(
                    "API-Football ingestion is disabled. Set app.api-football.enabled=true.");
        }
        if (!StringUtils.hasText(properties.getApiKey())
                || "mock".equalsIgnoreCase(properties.getApiKey().trim())) {
            throw new IllegalStateException(
                    "API-Football API key is not configured. Set API_FOOTBALL_API_KEY.");
        }
        if (properties.getLeagueIds() == null || properties.getLeagueIds().isEmpty()) {
            throw new IllegalStateException(
                    "No leagues configured. Set API_FOOTBALL_LEAGUE_IDS to comma-separated API-Football league IDs.");
        }
    }
}
