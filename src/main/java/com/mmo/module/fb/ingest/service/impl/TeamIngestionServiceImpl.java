package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.domain.entity.Season;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.repository.SeasonRepository;
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
    private final LeagueIngestionService leagueIngestionService;
    private final SeasonRepository seasonRepository;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final TeamPersistenceService teamPersistenceService;

    @Override
    public void syncTeams() {
        syncTeamsForProvider(appProperties.getIngestion().getProviders().getTeam());
    }

    @Override
    public void syncTeamsForProvider(Provider selectedProvider) {
        if (selectedProvider == Provider.API_FOOTBALL
                && (appProperties.getApiFootball() == null || !appProperties.getApiFootball().isEnabled())) return;
        leagueIngestionService.syncLeaguesForProvider(selectedProvider);
        Map<Long, String> leagueExternalIdsByLeagueId = new HashMap<>();
        leagueExternalIdRepository.findAllByProvider(selectedProvider).forEach(mapping ->
                leagueExternalIdsByLeagueId.put(mapping.getLeague().getId(), mapping.getExternalId()));
        for (Season season : seasonRepository.findAll()) {
            if (season.getLeague() == null || season.getYear() == null) continue;
            Long leagueExternalId;
            try {
                leagueExternalId = Long.valueOf(leagueExternalIdsByLeagueId.get(season.getLeague().getId()));
            } catch (RuntimeException ex) {
                continue;
            }
            List<ProviderTeam> teams = providerRegistry.team(selectedProvider).fetchTeams(
                    new TeamQuery(leagueExternalId, season.getYear()));
            teamPersistenceService.persistTeamsForSeason(selectedProvider, season, teams);
        }
    }
}
