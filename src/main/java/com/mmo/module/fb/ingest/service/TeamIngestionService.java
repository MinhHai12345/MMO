package com.mmo.module.fb.ingest.service;

import com.mmo.AppProperties;
import com.mmo.module.fb.domain.entity.LeagueExternalId;
import com.mmo.module.fb.domain.entity.Season;
import com.mmo.module.fb.domain.entity.Team;
import com.mmo.module.fb.domain.entity.TeamExternalId;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.repository.SeasonRepository;
import com.mmo.module.fb.domain.repository.TeamExternalIdRepository;
import com.mmo.module.fb.domain.repository.TeamRepository;
import com.mmo.module.fb.ingest.provider.model.ProviderTeam;
import com.mmo.module.fb.ingest.provider.model.TeamQuery;
import com.mmo.module.fb.ingest.provider.spi.TeamProvider;
import com.mmo.module.fb.ingest.provider.spi.ProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamIngestionService {

    private final ProviderRegistry providerRegistry;
    private final AppProperties appProperties;
    private final SeasonRepository seasonRepository;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final TeamRepository teamRepository;
    private final TeamExternalIdRepository teamExternalIdRepository;

    @Transactional
    public void syncTeams() {
        Provider selectedProvider = appProperties.getIngestion().getProviders().getTeam();
        if (selectedProvider == Provider.API_FOOTBALL && (appProperties.getApiFootball() == null || !appProperties.getApiFootball().isEnabled())) return;

        int synced = 0;
        for (Season season : seasonRepository.findAll()) {
            if (season.getLeague() == null || season.getYear() == null) continue;
            Long leagueExternalId = leagueExternalIdRepository
                    .findByLeagueIdAndProvider(season.getLeague().getId(), selectedProvider)
                    .map(LeagueExternalId::getExternalId)
                    .map(TeamIngestionService::parseLong)
                    .orElse(null);
            if (leagueExternalId == null) continue;

            List<ProviderTeam> teams = providerRegistry.team(selectedProvider).fetchTeams(
                    new TeamQuery(leagueExternalId, season.getYear()));
            for (ProviderTeam source : teams) {
                Team team = teamExternalIdRepository
                        .findByProviderAndExternalId(selectedProvider, source.externalId())
                        .map(TeamExternalId::getTeam)
                        .orElseGet(() -> teamRepository.findByName(source.name()).orElseGet(Team::new));

                team.setName(source.name());
                team.setShortName(source.shortName());
                team.setSlug(slug(source.name()) + "-" + source.externalId());
                team.setLogoUrl(source.logoUrl());
                team.setExternalId(source.externalId());
                team.setFoundedYear(source.foundedYear());
                team.setIsActive(true);
                if (source.stadiumName() != null || source.stadiumCapacity() != null) {
                    team.setStadiumName(source.stadiumName());
                    team.setStadiumCapacity(source.stadiumCapacity());
                }
                team = teamRepository.save(team);

                Team savedTeam = team;
                TeamExternalId external = teamExternalIdRepository
                        .findByTeamIdAndProvider(savedTeam.getId(), selectedProvider)
                        .orElseGet(() -> TeamExternalId.builder()
                                .team(savedTeam)
                                .provider(selectedProvider)
                                .externalId(source.externalId())
                                .build());
                external.setExternalId(source.externalId());
                teamExternalIdRepository.save(external);
                synced++;
            }
        }
        log.info("Team ingestion via {} completed. {} records saved/updated.", selectedProvider, synced);
    }



    private static Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String slug(String value) {
        if (value == null) return "unknown";
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
