package com.mmo.module.fb.ingest.service;

import com.mmo.AppProperties;
import com.mmo.module.fb.domain.entity.Season;
import com.mmo.module.fb.domain.entity.Team;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.repository.SeasonRepository;
import com.mmo.module.fb.domain.repository.TeamExternalIdRepository;
import com.mmo.module.fb.ingest.provider.model.FixtureQuery;
import com.mmo.module.fb.ingest.provider.model.ProviderFixture;
import com.mmo.module.fb.ingest.provider.spi.ProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FixtureIngestionService {

    private final ProviderRegistry providerRegistry;
    private final AppProperties appProperties;
    private final TeamIngestionService teamIngestionService;
    private final SeasonRepository seasonRepository;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final TeamExternalIdRepository teamExternalIdRepository;
    private final FixturePersistenceService fixturePersistenceService;

    public void syncCurrentSeasonFixtures(LocalDate from, LocalDate to) {
        syncFixturesForProvider(appProperties.getIngestion().getProviders().getFixture(), from, to);
    }

    /**
     * Coordinates upstream calls only. Each fixture is persisted in its own short transaction,
     * so slow provider requests do not hold a database transaction/connection open.
     */
    public void syncFixturesForProvider(Provider selectedProvider, LocalDate from, LocalDate to) {
        if (selectedProvider == Provider.API_FOOTBALL
                && (appProperties.getApiFootball() == null || !appProperties.getApiFootball().isEnabled())) {
            return;
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must be before or equal to 'to'");
        }

        teamIngestionService.syncTeamsForProvider(selectedProvider);

        List<Season> seasons = seasonRepository.findByIsCurrentTrueAndLeagueIsNotNullAndYearIsNotNull();
        Map<Long, String> leagueExternalIdsByLeagueId = new HashMap<>();
        leagueExternalIdRepository.findAllByProvider(selectedProvider).forEach(mapping ->
                leagueExternalIdsByLeagueId.put(mapping.getLeague().getId(), mapping.getExternalId()));
        Map<String, Long> teamIdsByExternalId = new HashMap<>();
        teamExternalIdRepository.findAllByProvider(selectedProvider).forEach(mapping ->
                teamIdsByExternalId.put(mapping.getExternalId(), mapping.getTeam().getId()));

        int saved = 0;
        int skipped = 0;
        for (Season season : seasons) {
            Long externalLeagueId = parseLong(leagueExternalIdsByLeagueId.get(season.getLeague().getId()));
            if (externalLeagueId == null) continue;

            // Provider I/O deliberately occurs outside any persistence transaction.
            List<ProviderFixture> fixtures = providerRegistry.fixture(selectedProvider).fetchFixtures(
                    new FixtureQuery(externalLeagueId, season.getYear(), from, to));
            log.info("Fetched {} fixtures for league {} season {} from {}",
                    fixtures.size(), externalLeagueId, season.getYear(), selectedProvider);

            for (ProviderFixture fixture : fixtures) {
                if (fixture.externalId() == null || fixture.externalId().isBlank()) {
                    skipped++;
                    log.warn("Skipping fixture with empty external ID from {}", selectedProvider);
                    continue;
                }
                Long homeTeamId = teamIdsByExternalId.get(fixture.homeTeamExternalId());
                Long awayTeamId = teamIdsByExternalId.get(fixture.awayTeamExternalId());
                if (homeTeamId == null || awayTeamId == null) {
                    skipped++;
                    log.debug("Skipping fixture {} because one or both teams are not mapped",
                            fixture.externalId());
                    continue;
                }
                try {
                    if (fixturePersistenceService.persistFixture(
                            season.getId(), homeTeamId, awayTeamId, selectedProvider, fixture)) {
                        saved++;
                    } else {
                        skipped++;
                    }
                } catch (Exception ex) {
                    skipped++;
                    log.warn("Failed to persist provider {} fixture {}: {}",
                            selectedProvider, fixture.externalId(), ex.getMessage());
                }
            }
        }

        log.info("Fixture ingestion via {} completed. {} saved/updated, {} skipped; date range {} to {}",
                selectedProvider, saved, skipped, from, to);
    }

    private static Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
