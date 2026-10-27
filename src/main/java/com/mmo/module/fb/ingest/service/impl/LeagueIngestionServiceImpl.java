package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.league.repository.LeagueExternalIdRepository;
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
    private final AppProperties appProperties;
    private final IngestionServiceSupport ingestionSupport;
    private final LeaguePersistenceService leaguePersistenceService;
    private final LeagueExternalIdRepository leagueExternalIdRepository;

    @Override
    public void syncLeagues() {
        syncLeaguesForProvider(appProperties.getIngestion().getProviders().getLeague());
    }

    @Override
    public void syncLeaguesForProvider(Provider selectedProvider) {
        ingestionSupport.requireProviderEnabled(selectedProvider, "league");
        List<ProviderLeague> leagues;
        try {
            leagues = providerRegistry.league(selectedProvider).fetchLeagues(List.of());
        } catch (FootballDataRestrictedResourceException ex) {
            if (selectedProvider != Provider.FOOTBALL_DATA) throw ex;
            var existingMappings = leagueExternalIdRepository.findAllByProvider(selectedProvider);
            if (existingMappings.isEmpty()) {
                throw new IllegalStateException(
                        "Cannot bootstrap FB: football-data.org restricts competitions and no local league mappings exist.",
                        ex);
            }
            log.warn("football-data.org restricts competitions for this subscription. Preserving {} existing local league mappings.",
                    existingMappings.size());
            return;
        }
        if (leagues == null || leagues.isEmpty()) {
            throw new IllegalStateException("Provider " + selectedProvider
                    + " returned no leagues; refusing to treat league ingestion as successful.");
        }
        leaguePersistenceService.persistLeagues(selectedProvider, leagues);
    }
}
