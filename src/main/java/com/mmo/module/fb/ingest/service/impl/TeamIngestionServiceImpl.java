package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.league.entity.Season;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.league.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.league.repository.SeasonRepository;
import com.mmo.module.fb.ingest.provider.apifootball.client.ApiFootballPlanRestrictionException;
import com.mmo.module.fb.ingest.provider.footballdata.client.FootballDataRestrictedResourceException;
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

    @Override
    public void syncTeams() {
        syncTeamsForProvider(appProperties.getIngestion().getProviders().getTeam());
    }

    @Override
    public void syncTeamsForProvider(Provider selectedProvider) {
        if (selectedProvider == Provider.FOOTBALL_DATA
                && (appProperties.getFootballData() == null || !appProperties.getFootballData().isEnabled())) {
            throw new IllegalStateException("football-data.org is disabled but was selected for team ingestion");
        }
        if (selectedProvider == Provider.API_FOOTBALL
                && (appProperties.getApiFootball() == null || !appProperties.getApiFootball().isEnabled())) {
            throw new IllegalStateException("API-Football is disabled but was selected for team ingestion");
        }

        // Team sync can run independently on a cold start, but should not refetch every
        // tournament and all season metadata when provider mappings already exist.
        if (leagueExternalIdRepository.findAllByProvider(selectedProvider).isEmpty()) {
            leagueIngestionService.syncLeaguesForProvider(selectedProvider);
        }
        ensureConfiguredFootballDataSeasons(selectedProvider);

        Map<Long, String> externalLeagueIdsByLeagueId = new HashMap<>();
        leagueExternalIdRepository.findAllByProvider(selectedProvider).forEach(mapping ->
                externalLeagueIdsByLeagueId.put(mapping.getLeague().getId(), mapping.getExternalId()));

        int fetched = 0;
        int failed = 0;
        for (Season season : seasonsToSync(selectedProvider)) {
            String externalLeagueId = externalLeagueIdsByLeagueId.get(season.getLeague().getId());
            if (externalLeagueId == null || externalLeagueId.isBlank()) {
                log.warn("Skipping teams for season {} and league {} because provider {} has no league ID mapping",
                        season.getYear(), season.getLeague().getId(), selectedProvider);
                failed++;
                continue;
            }

            try {
                Long parsedLeagueId = parseLong(externalLeagueId);
                if (parsedLeagueId == null) {
                    failed++;
                    log.error("Invalid provider league ID '{}' for provider {} season {}",
                            externalLeagueId, selectedProvider, season.getYear());
                    continue;
                }
                List<ProviderTeam> teams = providerRegistry.team(selectedProvider)
                        .fetchTeams(new TeamQuery(parsedLeagueId, season.getYear()));
                if (teams == null || teams.isEmpty()) {
                    log.warn("Provider {} returned no teams for league {} season {}",
                            selectedProvider, externalLeagueId, season.getYear());
                    continue;
                }
                teamPersistenceService.persistTeamsForSeason(selectedProvider, season, teams);
                fetched += teams.size();
            } catch (FootballDataRestrictedResourceException ex) {
                if (selectedProvider != Provider.FOOTBALL_DATA) throw ex;
                failed++;
                log.warn("Skipping teams for league {} season {} because football-data.org restricts this resource: {}",
                        externalLeagueId, season.getYear(), ex.getMessage());
            } catch (ApiFootballPlanRestrictionException ex) {
                failed++;
                log.warn("Skipping teams for league {} season {} because the API-Football plan does not include it: {}",
                        externalLeagueId, season.getYear(), ex.getMessage());
            }
        }
        log.info("Team ingestion via {} completed. fetched={}, failed/skipped={}",
                selectedProvider, fetched, failed);
    }

    private static Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void ensureConfiguredFootballDataSeasons(Provider selectedProvider) {
        List<String> years = configuredSeasonYears(selectedProvider);
        if (years.isEmpty()) return;

        leagueExternalIdRepository.findAllByProvider(selectedProvider).forEach(mapping -> {
            for (String year : years) {
                seasonRepository.findByLeague_IdAndYear(mapping.getLeague().getId(), year)
                        .orElseGet(() -> {
                            Season season = new Season();
                            season.setLeague(mapping.getLeague());
                            season.setYear(year);
                            season.setCurrent(false);
                            return seasonRepository.save(season);
                        });
            }
        });
    }

    private List<Season> seasonsToSync(Provider selectedProvider) {
        List<String> years = configuredSeasonYears(selectedProvider);
        if (!years.isEmpty()) {
            return seasonRepository.findAll().stream()
                    .filter(season -> season.getLeague() != null && season.getYear() != null
                            && years.contains(season.getYear()))
                    .toList();
        }
        return seasonRepository.findByIsCurrentTrueAndLeagueIsNotNullAndYearIsNotNull();
    }

    private List<String> configuredSeasonYears(Provider selectedProvider) {
        if (selectedProvider != Provider.FOOTBALL_DATA || appProperties.getFootballData() == null
                || appProperties.getFootballData().getSeasons() == null) {
            return List.of();
        }
        return appProperties.getFootballData().getSeasons().stream()
                .filter(year -> year != null && !year.isBlank())
                .map(String::trim).distinct().toList();
    }
}
