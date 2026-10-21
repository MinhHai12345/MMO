package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.domain.entity.Season;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.repository.SeasonRepository;
import com.mmo.module.fb.ingest.provider.apifootball.client.ApiFootballPlanRestrictionException;
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

    private void ensureConfiguredFootballDataSeasons(Provider selectedProvider) {
        List<String> years = configuredSeasonYears(selectedProvider);
        if (years.isEmpty()) return;

        leagueExternalIdRepository.findAllByProvider(selectedProvider).forEach(mapping -> {
            for (String year : years) {
                seasonRepository.findByLeague_IdAndYear(mapping.getLeague().getId(), year)
                        .orElseGet(() -> {
                            Season season = new Season();
                            season.setLeague(mapping.getLeague());
                            season.setYear(year);
                            season.setCurrent(false);
                            return seasonRepository.save(season);
                        });
            }
        });
    }

    private List<Season> seasonsToSync(Provider selectedProvider) {
        List<String> years = configuredSeasonYears(selectedProvider);
        if (!years.isEmpty()) {
            return seasonRepository.findAll().stream()
                    .filter(season -> season.getLeague() != null && season.getYear() != null
                            && years.contains(season.getYear()))
                    .toList();
        }
        return seasonRepository.findByIsCurrentTrueAndLeagueIsNotNullAndYearIsNotNull();
    }

    private List<String> configuredSeasonYears(Provider selectedProvider) {
        if (selectedProvider != Provider.FOOTBALL_DATA || appProperties.getFootballData() == null
                || appProperties.getFootballData().getSeasons() == null) {
            return List.of();
        }
        return appProperties.getFootballData().getSeasons().stream()
                .filter(year -> year != null && !year.isBlank())
                .map(String::trim).distinct().toList();
    }
}
