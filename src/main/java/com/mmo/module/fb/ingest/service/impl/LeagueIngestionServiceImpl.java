package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.domain.enums.Provider;
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
        List<Long> configuredIds = selectedProvider == Provider.API_FOOTBALL
                ? appProperties.getApiFootball().getLeagueIds() : List.of();
        if (selectedProvider == Provider.API_FOOTBALL && (configuredIds == null || configuredIds.isEmpty())) {
            log.warn("No API-Football league IDs configured; set API_FOOTBALL_LEAGUE_IDS.");
            return;
        }
        List<ProviderLeague> leagues = providerRegistry.league(selectedProvider).fetchLeagues(configuredIds);
        leaguePersistenceService.persistLeagues(selectedProvider, leagues);
    }
}
