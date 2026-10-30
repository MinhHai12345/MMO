package com.mmo.module.fb.ingest.service.impl;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.MmoProviderDataStandardizer;
import com.mmo.module.fb.ingest.provider.model.ProviderTeam;
import com.mmo.module.fb.ingest.service.ExternalIdMappingService;
import com.mmo.module.fb.ingest.service.TeamPersistenceService;
import com.mmo.module.fb.league.entity.Season;
import com.mmo.module.fb.team.entity.Team;
import com.mmo.module.fb.team.entity.TeamExternalId;
import com.mmo.module.fb.team.repository.TeamExternalIdRepository;
import com.mmo.module.fb.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamPersistenceServiceImpl implements TeamPersistenceService {

    private final TeamRepository teamRepository;
    private final TeamExternalIdRepository teamExternalIdRepository;
    private final ExternalIdMappingService externalIdMappingService;

    @Override
    @Transactional
    public void persistTeamsForSeason(Provider selectedProvider, Season season, List<ProviderTeam> teams) {
        int synced = 0;
        Map<String, Team> teamsByExternalId = new HashMap<>();
        Map<Long, TeamExternalId> mappingByTeamId = new HashMap<>();

        teamExternalIdRepository.findAllByProvider(selectedProvider).forEach(mapping -> {
            teamsByExternalId.put(mapping.getExternalId(), mapping.getTeam());
            mappingByTeamId.put(mapping.getTeam().getId(), mapping);
        });

        // Build one in-memory index of canonical teams. This lets UnderStat names
        // match teams already imported from Football-Data/API-Football/SofaScore.
        Map<String, List<Team>> teamsByNameKey = selectedProvider == Provider.UNDER_STATS
                ? indexCanonicalTeams(teamRepository.findAll()) : Map.of();

        for (ProviderTeam source : teams == null ? List.<ProviderTeam>of() : teams) {
            if (source == null || source.externalId() == null || source.externalId().isBlank()
                    || source.name() == null || source.name().isBlank()) {
                log.warn("Skipping team with empty external ID/name from {}", selectedProvider);
                continue;
            }

            Team team = teamsByExternalId.get(source.externalId());
            if (team == null && selectedProvider == Provider.UNDER_STATS) {
                List<Team> candidates = findNameMatches(source, teamsByNameKey);
                if (candidates.size() == 1) {
                    Team candidate = candidates.get(0);
                    if (!mappingByTeamId.containsKey(candidate.getId())) {
                        team = candidate;
                        log.debug("Matched UnderStat team '{}' ({}) to existing MMO team '{}' (id={})",
                                source.name(), source.externalId(), candidate.getName(), candidate.getId());
                    } else {
                        log.warn("Skipping UnderStat team '{}' ({}): matched MMO team '{}' (id={}) "
                                        + "already has an UnderStat external ID {}",
                                source.name(), source.externalId(), candidate.getName(), candidate.getId(),
                                mappingByTeamId.get(candidate.getId()).getExternalId());
                        continue;
                    }
                } else if (candidates.size() > 1) {
                    log.warn("Skipping ambiguous UnderStat team '{}' ({}): matched MMO teams {}",
                            source.name(), source.externalId(),
                            candidates.stream().map(candidate -> candidate.getId() + ":" + candidate.getName()).toList());
                    continue;
                }
            }
            if (team == null && selectedProvider != Provider.FOOTBALL_DATA
                    && selectedProvider != Provider.UNDER_STATS) {
                log.debug("Skipping unmapped team '{}' from {} (external ID {}). "
                                + "Register its provider ID against the canonical MMO team before syncing.",
                        source.name(), selectedProvider, source.externalId());
                continue;
            }
            if (team == null) {
                // Football-Data and UnderStat may bootstrap canonical teams. Other providers
                // are secondary sources and require an explicit cross-provider mapping.
                team = new Team();
            }

            TeamExternalId existingMapping = team.getId() == null ? null : mappingByTeamId.get(team.getId());
            if (existingMapping != null && !source.externalId().equals(existingMapping.getExternalId())) {
                log.warn("Skipping team '{}' from {} because canonical team {} is already mapped to provider ID {}",
                        source.name(), selectedProvider, team.getId(), existingMapping.getExternalId());
                continue;
            }

            if (selectedProvider == Provider.FOOTBALL_DATA || selectedProvider == Provider.UNDER_STATS) {
                if (team.getName() == null || team.getName().isBlank()) team.setName(source.name());
                if (team.getShortName() == null || team.getShortName().isBlank()) {
                    team.setShortName(source.shortName());
                }
                if (team.getSlug() == null || team.getSlug().isBlank()) {
                    team.setSlug(slug(source.name()) + "-" + selectedProvider.name().toLowerCase()
                            + "-" + source.externalId());
                }
                if (source.logoUrl() != null && !source.logoUrl().isBlank()) {
                    team.setLogoUrl(source.logoUrl());
                }
                if (source.foundedYear() != null) team.setFoundedYear(source.foundedYear());
                team.setIsActive(true);
                if (source.stadiumName() != null) team.setStadiumName(source.stadiumName());
                if (source.stadiumCapacity() != null) team.setStadiumCapacity(source.stadiumCapacity());
                team = teamRepository.save(team);
            }

            externalIdMappingService.registerTeam(team, selectedProvider, source.externalId());
            TeamExternalId savedMapping = teamExternalIdRepository
                    .findByTeamIdAndProvider(team.getId(), selectedProvider).orElseThrow();
            teamsByExternalId.put(source.externalId(), team);
            mappingByTeamId.put(team.getId(), savedMapping);
            if (selectedProvider == Provider.UNDER_STATS) {
                addTeamToNameIndex(teamsByNameKey, team);
            }
            synced++;
        }
        log.info("Team persistence via {} completed. {} records saved/updated.", selectedProvider, synced);
    }

    private static Map<String, List<Team>> indexCanonicalTeams(List<Team> teams) {
        Map<String, List<Team>> index = new HashMap<>();
        for (Team team : teams) addTeamToNameIndex(index, team);
        return index;
    }

    private static void addTeamToNameIndex(Map<String, List<Team>> index, Team team) {
        if (team == null || team.getId() == null) return;
        addNameKey(index, team.getName(), team);
        addNameKey(index, team.getShortName(), team);
    }

    private static void addNameKey(Map<String, List<Team>> index, String name, Team team) {
        String key = MmoProviderDataStandardizer.teamNameMatchKey(name);
        if (key == null || key.isBlank()) return;
        List<Team> matches = index.computeIfAbsent(key, ignored -> new ArrayList<>());
        if (matches.stream().noneMatch(existing -> existing.getId().equals(team.getId()))) {
            matches.add(team);
        }
    }

    private static List<Team> findNameMatches(ProviderTeam source, Map<String, List<Team>> index) {
        Set<Long> matchedIds = new HashSet<>();
        List<Team> matches = new ArrayList<>();
        for (String sourceName : List.of(
                source.name(),
                source.shortName() == null ? "" : source.shortName())) {
            String key = MmoProviderDataStandardizer.teamNameMatchKey(sourceName);
            if (key == null || key.isBlank()) continue;
            for (Team candidate : index.getOrDefault(key, List.of())) {
                if (matchedIds.add(candidate.getId())) matches.add(candidate);
            }
        }
        return matches;
    }

    private static String slug(String value) {
        if (value == null) return "unknown";
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
