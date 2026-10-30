package com.mmo.module.fb.ingest.service.impl;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.ProviderLeague;
import com.mmo.module.fb.ingest.provider.model.ProviderSeason;
import com.mmo.module.fb.ingest.provider.model.SeasonQuery;
import com.mmo.module.fb.ingest.provider.spi.ProviderRegistry;
import com.mmo.module.fb.ingest.service.LeaguePersistenceService;
import com.mmo.module.fb.ingest.service.SeasonIngestionService;
import com.mmo.module.fb.league.entity.LeagueExternalId;
import com.mmo.module.fb.league.repository.LeagueExternalIdRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/** Orchestrates season discovery separately from league discovery. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeasonIngestionServiceImpl implements SeasonIngestionService {
    private final ProviderRegistry providerRegistry;
    private final IngestionServiceSupport ingestionSupport;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final LeaguePersistenceService leaguePersistenceService;

    @Override
    public void syncSeasonsForProvider(Provider selectedProvider) {
        ingestionSupport.requireProviderEnabled(selectedProvider, "season");
        if (!providerRegistry.hasSeasonProvider(selectedProvider)) {
            log.info("Provider {} exposes seasons through its league resource; standalone season stage is skipped",
                    selectedProvider);
            return;
        }

        List<LeagueExternalId> leagueMappings = leagueExternalIdRepository.findAllByProvider(selectedProvider);
        if (leagueMappings.isEmpty()) {
            throw new IllegalStateException("Cannot ingest seasons from " + selectedProvider
                    + " before its league mappings have been synchronized");
        }

        int discovered = 0;
        int failed = 0;
        for (LeagueExternalId mapping : leagueMappings) {
            if (mapping.getLeague() == null || !StringUtils.hasText(mapping.getExternalId())) continue;
            try {
                List<ProviderSeason> seasons = providerRegistry.season(selectedProvider).fetchSeasons(
                        new SeasonQuery(mapping.getExternalId(), mapping.getIdentify()));
                if (seasons == null || seasons.isEmpty()) {
                    log.warn("Provider {} returned no seasons for league externalId={}",
                            selectedProvider, mapping.getExternalId());
                    continue;
                }
                var league = mapping.getLeague();
                ProviderLeague sourceLeague = new ProviderLeague(
                        mapping.getExternalId(), mapping.getIdentify(), league.getName(),
                        league.getCountry(), league.getLogoUrl(), seasons);
                leaguePersistenceService.persistLeagues(selectedProvider, List.of(sourceLeague));
                discovered += seasons.size();
            } catch (RuntimeException ex) {
                failed++;
                log.error("Season ingestion failed for provider {} leagueExternalId={}: {}",
                        selectedProvider, mapping.getExternalId(), ex.getMessage(), ex);
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
