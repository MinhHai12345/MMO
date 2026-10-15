package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.domain.entity.Season;
import com.mmo.module.fb.domain.entity.TeamExternalId;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.repository.SeasonRepository;
import com.mmo.module.fb.domain.repository.TeamExternalIdRepository;
import com.mmo.module.fb.ingest.provider.model.ProviderStanding;
import com.mmo.module.fb.ingest.provider.model.StandingsQuery;
import com.mmo.module.fb.ingest.provider.spi.ProviderRegistry;
import com.mmo.module.fb.ingest.provider.spi.StandingsProvider;
import com.mmo.module.fb.ingest.service.StandingsIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.mmo.module.fb.ingest.service.StandingsPersistenceService;
import com.mmo.module.fb.ingest.service.TeamIngestionService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StandingsIngestionServiceImpl implements StandingsIngestionService {

    private final ProviderRegistry providerRegistry;
    private final AppProperties appProperties;
    private final TeamIngestionService teamIngestionService;
    private final SeasonRepository seasonRepository;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final TeamExternalIdRepository teamExternalIdRepository;
    private final StandingsPersistenceService standingsPersistenceService;

    public void syncStandings() {
        syncStandingsForProvider(appProperties.getIngestion().getProviders().getStandings());
    }

    /**
     * Fetch provider data outside a database transaction; each season's rows are persisted
     * in a separate short transaction by StandingsPersistenceService.
     */
    public void syncStandingsForProvider(Provider selectedProvider) {
        if (selectedProvider == Provider.API_FOOTBALL
                && (appProperties.getApiFootball() == null || !appProperties.getApiFootball().isEnabled())) {
            return;
        }

        teamIngestionService.syncTeamsForProvider(selectedProvider);
        StandingsProvider provider = providerRegistry.standings(selectedProvider);

        Map<Long, String> leagueExternalIdsByLeagueId = new HashMap<>();
        leagueExternalIdRepository.findAllByProvider(selectedProvider).forEach(mapping ->
                leagueExternalIdsByLeagueId.put(mapping.getLeague().getId(), mapping.getExternalId()));

        Map<String, Long> teamIdsByExternalId = new HashMap<>();
        for (TeamExternalId mapping : teamExternalIdRepository.findAllByProvider(selectedProvider)) {
            teamIdsByExternalId.put(mapping.getExternalId(), mapping.getTeam().getId());
        }

        int saved = 0;
        List<Season> seasons = seasonRepository.findAll();
        for (Season season : seasons) {
            if (season.getLeague() == null || season.getYear() == null) continue;
            Long externalLeagueId = parseLong(leagueExternalIdsByLeagueId.get(season.getLeague().getId()));
            if (externalLeagueId == null) continue;

            // This network call happens without a transaction.
            List<ProviderStanding> rows = provider.fetchStandings(
                    new StandingsQuery(externalLeagueId, season.getYear()));
            for (ProviderStanding row : rows) {
                Long teamId = teamIdsByExternalId.get(row.teamExternalId());
                if (teamId == null) continue;
                standingsPersistenceService.upsertStanding(season.getId(), teamId, row);
                saved++;
            }
        }
        log.info("Standings ingestion completed. {} rows upserted.", saved);
    }

    private static Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
