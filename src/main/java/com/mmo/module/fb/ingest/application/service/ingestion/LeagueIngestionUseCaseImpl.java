package com.mmo.module.fb.ingest.application.service.ingestion;

import com.mmo.module.fb.ingest.application.exception.ProviderAccessException;
import com.mmo.module.fb.ingest.application.service.support.IngestionServiceSupport;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.ProviderLeague;
import com.mmo.module.fb.ingest.application.registry.ProviderIngestionRegistry;
import com.mmo.module.fb.ingest.application.port.in.LeagueIngestionUseCase;
import com.mmo.module.fb.ingest.application.port.out.LeaguePersistencePort;
import com.mmo.module.fb.ingest.application.port.out.LeagueExternalMappingPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeagueIngestionUseCaseImpl implements LeagueIngestionUseCase {
    private final ProviderIngestionRegistry providerRegistry;
    private final AppProperties appProperties;
    private final IngestionServiceSupport ingestionSupport;
    private final LeaguePersistencePort leaguePersistenceService;
    private final LeagueExternalMappingPort leagueExternalMappingPort;

    @Override
    public void syncLeagues() {
        syncLeaguesForProvider(appProperties.getIngestion().getProviders().getLeague());
    }

    @Override
    public void syncLeaguesForProvider(Provider selectedProvider) {
        ingestionSupport.requireProviderEnabled(selectedProvider, "league");
        List<ProviderLeague> leagues;
        try {
            // Football-Data competition IDs and SofaScore tournament IDs belong to different
            // namespaces. Never pass the Football-Data allow-list to SofaScore: discover its
            // tournament catalogue and let LeaguePersistencePort match canonical leagues by
            // name/country before creating provider mappings.
            List<Long> externalLeagueIds = selectedProvider == Provider.SOFA_SCORE
                    || selectedProvider == Provider.UNDER_STATS
                    ? List.of()
                    : List.of(2000L, 2001L, 2002L, 2014L, 2015L, 2019L, 2016L, 2021L);
            leagues = providerRegistry.league(selectedProvider).fetchLeagues(externalLeagueIds);
        } catch (ProviderAccessException ex) {
            if (!ex.is(ProviderAccessException.Kind.RESOURCE_RESTRICTED)
                    || selectedProvider != Provider.FOOTBALL_DATA) throw ex;
            var existingMappings = leagueExternalMappingPort.findAllByProvider(selectedProvider);
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
