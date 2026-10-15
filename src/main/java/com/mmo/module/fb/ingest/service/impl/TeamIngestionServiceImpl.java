package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
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
import com.mmo.module.fb.ingest.provider.spi.ProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamIngestionServiceImpl implements com.mmo.module.fb.ingest.service.TeamIngestionService {

    private final ProviderRegistry providerRegistry;
    private final AppProperties appProperties;
    private final LeagueIngestionService leagueIngestionService;
    private final SeasonRepository seasonRepository;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final TeamRepository teamRepository;
    private final TeamExternalIdRepository teamExternalIdRepository;

    public void syncTeams() {
        syncTeamsForProvider(appProperties.getIngestion().getProviders().getTeam());
    }

    /**
     * Synchronizes teams for the requested source. League IDs for this source are prepared first,
     * so team ingestion can work even when league ingestion uses a different provider.
     */
    @Transactional
    public void syncTeamsForProvider(Provider selectedProvider) {
        if (selectedProvider == Provider.API_FOOTBALL
                && (appProperties.getApiFootball() == null || !appProperties.getApiFootball().isEnabled())) {
            return;
        }
        leagueIngestionService.syncLeaguesForProvider(selectedProvider);

        int synced = 0;
        Map<Long, String> leagueExternalIdsByLeagueId = new HashMap<>();
        leagueExternalIdRepository.findAllByProvider(selectedProvider).forEach(mapping ->
                leagueExternalIdsByLeagueId.put(mapping.getLeague().getId(), mapping.getExternalId()));
        List<Team> knownTeams = new ArrayList<>(teamRepository.findAll());
        Map<String, Team> teamsByExternalId = new HashMap<>();
        Map<Long, TeamExternalId> mappingByTeamId = new HashMap<>();
        teamExternalIdRepository.findAllByProvider(selectedProvider).forEach(mapping -> {
            teamsByExternalId.put(mapping.getExternalId(), mapping.getTeam());
            mappingByTeamId.put(mapping.getTeam().getId(), mapping);
        });

        for (Season season : seasonRepository.findAll()) {
            if (season.getLeague() == null || season.getYear() == null) continue;
            Long leagueExternalId = parseLong(leagueExternalIdsByLeagueId.get(season.getLeague().getId()));
            if (leagueExternalId == null) continue;

            List<ProviderTeam> teams = providerRegistry.team(selectedProvider).fetchTeams(
                    new TeamQuery(leagueExternalId, season.getYear()));
            for (ProviderTeam source : teams) {
                if (source.externalId() == null || source.externalId().isBlank()) {
                    log.warn("Skipping team with empty external ID from {}", selectedProvider);
                    continue;
                }
                Team team = teamsByExternalId.get(source.externalId());

                if (team == null) {
                    List<Team> candidates = knownTeams.stream()
                            .filter(existing -> sameTeamName(existing.getName(), source.name())
                                    || sameTeamName(existing.getShortName(), source.name())
                                    || sameTeamName(existing.getName(), source.shortName()))
                            .toList();
                    if (candidates.size() > 1) {
                        log.warn("Ambiguous team mapping for provider {} external ID {} name '{}'; skipping",
                                selectedProvider, source.externalId(), source.name());
                        continue;
                    }
                    team = candidates.isEmpty() ? new Team() : candidates.get(0);
                }

                if (team.getName() == null || team.getName().isBlank()) {
                    team.setName(source.name());
                }
                if (team.getShortName() == null || team.getShortName().isBlank()) {
                    team.setShortName(source.shortName());
                }
                if (team.getSlug() == null || team.getSlug().isBlank()) {
                    team.setSlug(slug(source.name()) + "-" + selectedProvider.name().toLowerCase()
                            + "-" + source.externalId());
                }
                team.setLogoUrl(source.logoUrl());
                if (team.getExternalId() == null || team.getExternalId().isBlank()) {
                    team.setExternalId(source.externalId());
                }
                team.setFoundedYear(source.foundedYear());
                team.setIsActive(true);
                if (source.stadiumName() != null || source.stadiumCapacity() != null) {
                    team.setStadiumName(source.stadiumName());
                    team.setStadiumCapacity(source.stadiumCapacity());
                }
                team = teamRepository.save(team);

                Team savedTeam = team;
                TeamExternalId external = mappingByTeamId.get(savedTeam.getId());
                if (external == null) {
                    external = TeamExternalId.builder()
                            .team(savedTeam)
                            .provider(selectedProvider)
                            .externalId(source.externalId())
                            .build();
                }
                external.setExternalId(source.externalId());
                teamExternalIdRepository.save(external);
                teamsByExternalId.put(source.externalId(), savedTeam);
                mappingByTeamId.put(savedTeam.getId(), external);
                if (knownTeams.stream().noneMatch(existing -> existing.getId().equals(savedTeam.getId()))) {
                    knownTeams.add(savedTeam);
                }
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

    private static boolean sameTeamName(String left, String right) {
        return left != null && right != null
                && left.toLowerCase().replaceAll("[^a-z0-9]", "")
                .equals(right.toLowerCase().replaceAll("[^a-z0-9]", ""));
    }

    private static String slug(String value) {
        if (value == null) return "unknown";
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
