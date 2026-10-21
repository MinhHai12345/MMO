package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.repository.LeagueExternalIdRepository;
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
    private final LeagueExternalIdRepository leagueExternalIdRepository;

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
            throw new IllegalStateException("No competition IDs configured for provider " + selectedProvider
                    + "; configure its league/competition ID list before ingestion.");
        }

        List<ProviderLeague> leagues;
        try {
            leagues = providerRegistry.league(selectedProvider).fetchLeagues(configuredIds);
        } catch (FootballDataRestrictedResourceException ex) {
            if (selectedProvider != Provider.FOOTBALL_DATA) throw ex;

            boolean hasExistingMappings = !leagueExternalIdRepository
                    .findAllByProvider(Provider.FOOTBALL_DATA).isEmpty();
            if (!hasExistingMappings) {
                throw new IllegalStateException(
                        "Cannot bootstrap core football data: football-data.org restricts the competitions "
                                + "resource and no existing football-data.org league mappings are available. "
                                + "Use an accessible subscription/resource or configure another provider.",
                        ex);
            }

            log.warn("football-data.org restricts the competitions resource for this subscription. "
                    + "Keeping {} existing league mappings and continuing with local data. {}",
                    leagueExternalIdRepository.findAllByProvider(Provider.FOOTBALL_DATA).size(),
                    ex.getMessage());
            return;
        }

        if (leagues == null || leagues.isEmpty()) {
            throw new IllegalStateException("Provider " + selectedProvider
                    + " returned no leagues; refusing to mark league ingestion as successful.");
        }
        leaguePersistenceService.persistLeagues(selectedProvider, leagues);
    }
}
