package com.mmo.module.fb.ingest.application.resource;

import com.mmo.module.fb.ingest.application.support.IngestionServiceSupport;

import com.mmo.AppProperties;
import com.mmo.module.fb.league.entity.Season;
import com.mmo.module.fb.league.entity.LeagueExternalId;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.Dataset;
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
        syncTeams(selectedProvider, false);
    }

    @Override
    public void syncAllSeasonsForProvider(Provider selectedProvider) {
        syncTeams(selectedProvider, true);
    }

    private void syncTeams(Provider selectedProvider, boolean includeHistoricalSeasons) {
        ingestionSupport.requireProviderEnabled(selectedProvider, "team");

        // Team sync can run independently on a cold start, but should not refetch every
        // tournament and all season metadata when provider mappings already exist.
        if (leagueExternalIdRepository.findAllByProvider(selectedProvider).isEmpty()) {
            leagueIngestionService.syncLeaguesForProvider(selectedProvider);
        }

        Map<Long, LeagueExternalId> leagueMappingsByLeagueId = new HashMap<>();
        leagueExternalIdRepository.findAllByProvider(selectedProvider).forEach(mapping ->
                leagueMappingsByLeagueId.put(mapping.getLeague().getId(), mapping));

        int fetched = 0;
        int failed = 0;
        int skipped = 0;
        List<Season> seasons = includeHistoricalSeasons
                ? ingestionSupport.allMappedSeasonsToSync(selectedProvider, Dataset.TEAM)
                : ingestionSupport.seasonsToSync(selectedProvider, Dataset.TEAM);
        if (seasons.isEmpty() && selectedProvider == Provider.FOOTBALL_DATA) {
            throw new IllegalStateException("No mapped seasons available for Football-Data team ingestion");
        }
        for (Season season : seasons) {
            LeagueExternalId leagueMapping = leagueMappingsByLeagueId.get(season.getLeague().getId());
            String externalLeagueId = leagueMapping == null ? null : leagueMapping.getExternalId();
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
                        .fetchTeams(new TeamQuery(parsedLeagueId, season.getYear(), leagueMapping.getIdentify()));
                if (teams == null || teams.isEmpty()) {
                    log.warn("Provider {} returned no teams for league {} season {}",
                            selectedProvider, externalLeagueId, season.getYear());
                    continue;
                }
                teamPersistenceService.persistTeamsForSeason(selectedProvider, season, teams);
                fetched += teams.size();
            } catch (FootballDataRestrictedResourceException ex) {
                if (selectedProvider != Provider.FOOTBALL_DATA) throw ex;
                skipped++;
                log.warn("Skipping teams for league {} season {} because football-data.org restricts this resource: {}",
                        externalLeagueId, season.getYear(), ex.getMessage());
            } catch (ApiFootballPlanRestrictionException ex) {
                skipped++;
                log.warn("Skipping teams for league {} season {} because the API-Football plan does not include it: {}",
                        externalLeagueId, season.getYear(), ex.getMessage());
            }
        }
        log.info("Team ingestion via {} completed. fetched={}, failed={}, restricted/skipped={}",
                selectedProvider, fetched, failed, skipped);
        if (selectedProvider == Provider.FOOTBALL_DATA && failed > 0) {
            throw new IllegalStateException("Football-Data team ingestion incomplete: failed=" + failed);
        }
    }



}
