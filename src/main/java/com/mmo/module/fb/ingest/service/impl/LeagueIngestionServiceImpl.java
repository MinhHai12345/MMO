package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.footballdata.client.FootballDataRestrictedResourceException;
import com.mmo.module.fb.ingest.provider.model.ProviderLeague;
import com.mmo.module.fb.ingest.provider.spi.ProviderRegistry;
import com.mmo.module.fb.ingest.service.LeagueIngestionService;
import com.mmo.module.fb.ingest.service.LeaguePersistenceService;
import com.mmo.module.fb.league.repository.LeagueExternalIdRepository;
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
    private final IngestionServiceSupport ingestionSupport;
    private final LeaguePersistenceService leaguePersistenceService;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final UnderStatCatalogSyncService underStatCatalogSyncService;

    @Override
    public void syncLeagues() {
        syncLeaguesForProvider(appProperties.getIngestion().getProviders().getLeague());
    }

    @Override
    public void syncLeaguesForProvider(Provider selectedProvider) {
        ingestionSupport.requireProviderEnabled(selectedProvider, "league");
        if (selectedProvider == Provider.UNDER_STATS) {
            // UnderStat's stat-data endpoint discovers the catalogue; its season payload
            // also imports teams and player-season data. Run it as part of bootstrap's
            // existing league stage rather than through a dedicated scheduled job.
            underStatCatalogSyncService.syncCatalog();
            return;
        }
        List<ProviderLeague> leagues;
        try {
            // Football-Data competition IDs and SofaScore tournament IDs belong to different
            // namespaces. Never pass the Football-Data allow-list to SofaScore: discover its
            // tournament catalogue and let LeaguePersistenceService match canonical leagues by
            // name/country before creating provider mappings.
            List<Long> externalLeagueIds = selectedProvider == Provider.SOFA_SCORE
                    || selectedProvider == Provider.UNDER_STATS
                    ? List.of()
                    : List.of(2000L, 2001L, 2002L, 2014L, 2015L, 2019L, 2016L, 2021L);
            leagues = providerRegistry.league(selectedProvider).fetchLeagues(externalLeagueIds);
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
