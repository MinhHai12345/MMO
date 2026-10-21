package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.domain.entity.Season;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.repository.SeasonRepository;
import com.mmo.module.fb.ingest.provider.apifootball.client.ApiFootballPlanRestrictionException;
import com.mmo.module.fb.ingest.provider.model.ProviderTeam;
import com.mmo.module.fb.ingest.provider.model.TeamQuery;
import com.mmo.module.fb.ingest.provider.spi.ProviderRegistry;
import com.mmo.module.fb.ingest.service.LeagueIngestionService;
import com.mmo.module.fb.ingest.service.TeamIngestionService;
import com.mmo.module.fb.ingest.service.TeamPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamIngestionServiceImpl implements TeamIngestionService {
    private final ProviderRegistry providerRegistry;
    private final AppProperties appProperties;
    private final LeagueIngestionService leagueIngestionService;
    private final SeasonRepository seasonRepository;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final TeamPersistenceService teamPersistenceService;

    private void ensureConfiguredFootballDataSeasons(Provider selectedProvider) {
        if (selectedProvider != Provider.FOOTBALL_DATA || appProperties.getFootballData().getSeasons() == null
                || appProperties.getFootballData().getSeasons().isEmpty()) {
            return;
        }

        leagueExternalIdRepository.findAllByProvider(selectedProvider).forEach(mapping -> {
            for (String year : appProperties.getFootballData().getSeasons()) {
                if (year == null || year.isBlank()) continue;
                seasonRepository.findByLeague_IdAndYear(mapping.getLeague().getId(), year.trim())
                        .orElseGet(() -> {
                            Season season = new Season();
                            season.setLeague(mapping.getLeague());
                            season.setYear(year.trim());
                            season.setCurrent(false);
                            return seasonRepository.save(season);
                        });
            }
        });
    }

    private List<Season> seasonsToSync(Provider selectedProvider) {
        if (selectedProvider == Provider.FOOTBALL_DATA && appProperties.getFootballData().getSeasons() != null
                && !appProperties.getFootballData().getSeasons().isEmpty()) {
            List<String> years = appProperties.getFootballData().getSeasons().stream()
                    .filter(year -> year != null && !year.isBlank())
                    .map(String::trim).distinct().toList();
            return seasonRepository.findAll().stream()
                    .filter(season -> season.getLeague() != null && season.getYear() != null
                            && years.contains(season.getYear()))
                    .toList();
        }
        return seasonRepository.findByIsCurrentTrueAndLeagueIsNotNullAndYearIsNotNull();
    }

    @Override
    public void syncTeams() {
        syncTeamsForProvider(appProperties.getIngestion().getProviders().getTeam());
    }

    @Override
    public void syncTeamsForProvider(Provider selectedProvider) {
        if (selectedProvider == Provider.FOOTBALL_DATA
                && (appProperties.getFootballData() == null || !appProperties.getFootballData().isEnabled())) {
            log.info("Skipping team ingestion because football-data.org is disabled");
            return;
        }
        if (selectedProvider == Provider.API_FOOTBALL
                && (appProperties.getApiFootball() == null || !appProperties.getApiFootball().isEnabled())) {
            log.info("Skipping team ingestion because API-Football is disabled");
            return;
        }

        // Bootstrap missing prerequisites only. Do not consume provider quota by re-syncing
        // every league whenever the scheduled team job runs.
        if (leagueExternalIdRepository.findAllByProvider(selectedProvider).isEmpty()) {
            leagueIngestionService.syncLeaguesForProvider(selectedProvider);
        }

        ensureConfiguredFootballDataSeasons(selectedProvider);

        Map<Long, String> leagueExternalIdsByLeagueId = new HashMap<>();
        leagueExternalIdRepository.findAllByProvider(selectedProvider).forEach(mapping ->
                leagueExternalIdsByLeagueId.put(mapping.getLeague().getId(), mapping.getExternalId()));

        int saved = 0;
        int skipped = 0;
        // Historical seasons are opt-in for football-data.org to keep the Free API quota bounded.
        for (Season season : seasonsToSync(selectedProvider)) {
            if (season.getLeague() == null || season.getYear() == null) continue;
            Long leagueExternalId;
            try {
                leagueExternalId = Long.valueOf(leagueExternalIdsByLeagueId.get(season.getLeague().getId()));
            } catch (RuntimeException ex) {
                skipped++;
                continue;
            }
            List<ProviderTeam> teams;
            try {
                teams = providerRegistry.team(selectedProvider).fetchTeams(
                        new TeamQuery(leagueExternalId, season.getYear()));
            } catch (ApiFootballPlanRestrictionException ex) {
                skipped++;
                log.warn("Skipping team sync for league {} season {}: {}",
                        leagueExternalId, season.getYear(), ex.getMessage());
                continue;
            }
            teamPersistenceService.persistTeamsForSeason(selectedProvider, season, teams);
            saved += teams.size();
        }
        log.info("Team ingestion via {} completed. {} provider team records processed, {} seasons skipped",
                selectedProvider, saved, skipped);
    }
}
