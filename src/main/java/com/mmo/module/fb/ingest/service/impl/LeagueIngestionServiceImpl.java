package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.ingest.provider.footballdata.client.FootballDataRestrictedResourceException;
import com.mmo.module.fb.ingest.provider.model.ProviderLeague;
import com.mmo.module.fb.ingest.provider.spi.ProviderRegistry;
import com.mmo.module.fb.ingest.service.LeagueIngestionService;
import com.mmo.module.fb.ingest.service.LeaguePersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeagueIngestionServiceImpl implements LeagueIngestionService {
    private final ProviderRegistry providerRegistry;
    private final AppProperties appProperties;
    private final LeaguePersistenceService leaguePersistenceService;

    @Override
    public void syncLeagues() {
        syncLeaguesForProvider(appProperties.getIngestion().getProviders().getLeague());
    }

    @Override
    public void syncLeaguesForProvider(Provider selectedProvider) {
        if (selectedProvider == Provider.API_FOOTBALL
                && (appProperties.getApiFootball() == null || !appProperties.getApiFootball().isEnabled())) return;
        if (selectedProvider == Provider.FOOTBALL_DATA
                && (appProperties.getFootballData() == null || !appProperties.getFootballData().isEnabled())) return;
        List<Long> configuredIds = selectedProvider == Provider.API_FOOTBALL
                ? appProperties.getApiFootball().getLeagueIds()
                : selectedProvider == Provider.FOOTBALL_DATA
                    ? appProperties.getFootballData().getCompetitionIds() : List.of();
        if ((selectedProvider == Provider.API_FOOTBALL || selectedProvider == Provider.FOOTBALL_DATA)
                && (configuredIds == null || configuredIds.isEmpty())) {
            log.warn("No competition IDs configured for provider {}; configure its league/competition ID list.", selectedProvider);
            return;
        }

        List<ProviderLeague> leagues;
        try {
            leagues = providerRegistry.league(selectedProvider).fetchLeagues(configuredIds);
        } catch (FootballDataRestrictedResourceException ex) {
            if (selectedProvider != Provider.FOOTBALL_DATA) throw ex;
            log.warn("Skipping league ingestion because football-data.org restricts the competitions resource "
                    + "for the configured subscription. Existing local data is preserved. {}", ex.getMessage());
            return;
        }
        leaguePersistenceService.persistLeagues(selectedProvider, leagues);
    }
}
