package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.league.entity.Season;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.league.repository.LeagueExternalIdRepository;
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
    private final IngestionServiceSupport ingestionSupport;
    private final LeagueIngestionService leagueIngestionService;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final TeamPersistenceService teamPersistenceService;

    @Override
    public void syncTeams() {
        syncTeamsForProvider(appProperties.getIngestion().getProviders().getTeam());
    }

    @Override
    public void syncTeamsForProvider(Provider selectedProvider) {
        ingestionSupport.requireProviderEnabled(selectedProvider, "team");

        // Team sync can run independently on a cold start, but should not refetch every
        // tournament and all season metadata when provider mappings already exist.
        if (leagueExternalIdRepository.findAllByProvider(selectedProvider).isEmpty()) {
            leagueIngestionService.syncLeaguesForProvider(selectedProvider);
        }

        Map<Long, String> externalLeagueIdsByLeagueId = new HashMap<>();
        leagueExternalIdRepository.findAllByProvider(selectedProvider).forEach(mapping ->
                externalLeagueIdsByLeagueId.put(mapping.getLeague().getId(), mapping.getExternalId()));

        int fetched = 0;
        int failed = 0;
        for (Season season : ingestionSupport.seasonsToSync(selectedProvider)) {
            String externalLeagueId = externalLeagueIdsByLeagueId.get(season.getLeague().getId());
            if (externalLeagueId == null || externalLeagueId.isBlank()) {
                log.warn("Skipping teams for season {} and league {} because provider {} has no league ID mapping",
                        season.getYear(), season.getLeague().getId(), selectedProvider);
                failed++;
                continue;
            }

            try {
                Long parsedLeagueId = IngestionServiceSupport.parseExternalId(externalLeagueId);
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



}
