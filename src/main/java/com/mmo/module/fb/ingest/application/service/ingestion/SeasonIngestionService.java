package com.mmo.module.fb.ingest.application.service.ingestion;

import com.mmo.module.fb.ingest.application.service.support.IngestionServiceSupport;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.ProviderLeague;
import com.mmo.module.fb.ingest.provider.model.ProviderSeason;
import com.mmo.module.fb.ingest.provider.model.SeasonQuery;
import com.mmo.module.fb.ingest.application.registry.ProviderIngestionRegistry;
import com.mmo.module.fb.ingest.application.port.out.LeaguePersistencePort;
import com.mmo.module.fb.ingest.application.port.out.LeagueExternalMappingPort;
import com.mmo.module.fb.ingest.application.port.in.SeasonIngestionUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/** Orchestrates season discovery separately from league discovery. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeasonIngestionService implements SeasonIngestionUseCase {
    private final ProviderIngestionRegistry providerRegistry;
    private final IngestionServiceSupport ingestionSupport;
    private final LeagueExternalMappingPort leagueExternalMappingPort;
    private final LeaguePersistencePort leaguePersistencePort;

    @Override
    public void syncSeasonsForProvider(Provider selectedProvider) {
        ingestionSupport.requireProviderEnabled(selectedProvider, "season");
        if (!providerRegistry.hasSeasonProvider(selectedProvider)) {
            log.info("Provider {} exposes seasons through its league resource; standalone season stage is skipped",
                    selectedProvider);
            return;
        }

        List<LeagueExternalMappingPort.LeagueMapping> leagueMappings = leagueExternalMappingPort.findAllByProvider(selectedProvider);
        if (leagueMappings.isEmpty()) {
            throw new IllegalStateException("Cannot ingest seasons from " + selectedProvider
                    + " before its league mappings have been synchronized");
        }

        int discovered = 0;
        int failed = 0;
        for (LeagueExternalMappingPort.LeagueMapping mapping : leagueMappings) {
            if (mapping.leagueId() == null || !StringUtils.hasText(mapping.externalId())) continue;
            try {
                List<ProviderSeason> seasons = providerRegistry.season(selectedProvider).fetchSeasons(
                        new SeasonQuery(mapping.externalId(), mapping.identify()));
                if (seasons == null || seasons.isEmpty()) {
                    log.warn("Provider {} returned no seasons for league externalId={}",
                            selectedProvider, mapping.externalId());
                    continue;
                }
                
                ProviderLeague sourceLeague = new ProviderLeague(
                        mapping.externalId(), mapping.identify(), mapping.name(),
                        mapping.country(), mapping.logoUrl(), seasons);
                leaguePersistencePort.persistLeagues(selectedProvider, List.of(sourceLeague));
                discovered += seasons.size();
            } catch (RuntimeException ex) {
                failed++;
                log.error("Season ingestion failed for provider {} leagueExternalId={}: {}",
                        selectedProvider, mapping.externalId(), ex.getMessage(), ex);
            }
        }
        log.info("Season ingestion via {} completed. discovered={}, failed={}",
                selectedProvider, discovered, failed);
        if (failed > 0) {
            throw new IllegalStateException("Season ingestion via " + selectedProvider
                    + " incomplete: failed league requests=" + failed);
        }
    }
}
