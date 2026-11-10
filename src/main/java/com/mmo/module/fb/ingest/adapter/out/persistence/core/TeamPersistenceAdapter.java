package com.mmo.module.fb.ingest.adapter.out.persistence.core;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.ingest.adapter.out.provider.standardization.MmoProviderDataStandardizer;
import com.mmo.module.fb.ingest.application.model.provider.ProviderTeam;
import com.mmo.module.fb.ingest.application.port.out.TeamPersistencePort;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.team.entity.Team;
import com.mmo.module.fb.domain.team.entity.TeamExternalId;
import com.mmo.module.fb.domain.team.repository.TeamExternalIdRepository;
import com.mmo.module.fb.domain.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

@Slf4j
@Repository
@RequiredArgsConstructor
public class TeamPersistenceAdapter implements TeamPersistencePort {

    private final TeamRepository teamRepository;
    private final TeamExternalIdRepository teamExternalIdRepository;
    private final DataSourcePolicy dataSourcePolicy;

    @Override
    @Transactional
    public void persistTeamsForSeason(Provider selectedProvider, Season season, List<ProviderTeam> teams) {
        boolean routeAllowsCoreIngestion = season != null
                && (dataSourcePolicy.isPrimary(selectedProvider, Dataset.TEAM, season)
                || dataSourcePolicy.route(Dataset.TEAM, season).secondary().contains(selectedProvider));
        // SofaScore team ingestion is used here only to attach provider IDs to existing
        // canonical teams. It must never create or own canonical team data.
        boolean mappingOnlyProvider = selectedProvider == Provider.SOFA_SCORE;
        if (season == null || (!routeAllowsCoreIngestion && !mappingOnlyProvider)) {
            log.debug("Rejecting provider without team route: provider={}, season={}",
                    selectedProvider, season == null ? null : season.getYear());
            return;
        }

        Map<String, Team> teamsByExternalId = new HashMap<>();
        Map<Long, TeamExternalId> mappingByTeamId = new HashMap<>();
        teamExternalIdRepository.findAllByProvider(selectedProvider).forEach(mapping -> {
            teamsByExternalId.put(mapping.getExternalId(), mapping.getTeam());
            mappingByTeamId.put(mapping.getTeam().getId(), mapping);
        });
        Set<Long> footballDataOwnedTeamIds = teamExternalIdRepository
                .findAllByProvider(Provider.FOOTBALL_DATA).stream()
                .map(mapping -> mapping.getTeam().getId()).collect(java.util.stream.Collectors.toSet());

        // Every accepted provider resolves names against the same canonical MMO catalogue.
        // Provider-specific routes still decide whether a provider may create/update core data.
        Map<String, List<Team>> teamsByNameKey = indexCanonicalTeams(teamRepository.findAll());

        Set<Team> teamsToSave = new LinkedHashSet<>();
        Map<Team, String> pendingIdentityByTeam = new java.util.LinkedHashMap<>();
        int synced = 0;
        for (ProviderTeam source : teams == null ? List.<ProviderTeam>of() : teams) {
            if (source == null || source.externalId() == null || source.externalId().isBlank()
                    || source.name() == null || source.name().isBlank()) {
                log.warn("Skipping team with empty external ID/name from {}", selectedProvider);
                continue;
            }

            String canonicalName = MmoProviderDataStandardizer.standardTeamName(source.name());
            if (canonicalName == null || canonicalName.isBlank()) {
                log.warn("Skipping team with invalid canonical name from {} (external ID={})",
                        selectedProvider, source.externalId());
                continue;
            }
            ProviderTeam standardizedSource = new ProviderTeam(
                    source.externalId(),
                    canonicalName,
                    MmoProviderDataStandardizer.standardTeamName(source.shortName()),
                    source.logoUrl(),
                    source.foundedYear(),
                    source.stadiumName(),
                    source.stadiumCapacity());

            Team team = teamsByExternalId.get(standardizedSource.externalId());
            if (team == null) {
                List<Team> candidates = findNameMatches(standardizedSource, teamsByNameKey);
                if (candidates.size() == 1) {
                    Team candidate = candidates.get(0);
                    TeamExternalId existing = candidate.getId() == null
                            ? null : mappingByTeamId.get(candidate.getId());
                    if (existing == null) {
                        team = candidate;
                    } else {
                        log.warn("Skipping provider team '{}' ({}): matched MMO team '{}' (id={}) "
                                        + "already has a {} external ID {}",
                                source.name(), source.externalId(), candidate.getName(), candidate.getId(),
                                selectedProvider, existing.getExternalId());
                        continue;
                    }
                } else if (candidates.size() > 1) {
                    log.warn("Skipping ambiguous provider team '{}' ({}): matched MMO teams {}",
                            source.name(), source.externalId(),
                            candidates.stream().map(candidate -> candidate.getId() + ":" + candidate.getName()).toList());
                    continue;
                }
            }
            if (team == null && selectedProvider != Provider.FOOTBALL_DATA
                    && selectedProvider != Provider.UNDER_STATS
                    && selectedProvider != Provider.FOOTBALL_DATA_UK) {
                log.debug("Skipping unmapped team '{}' from {} (external ID {}). "
                                + "Register its provider ID against the canonical MMO team before syncing.",
                        source.name(), selectedProvider, source.externalId());
                continue;
            }

            boolean primaryOwner = dataSourcePolicy.isPrimary(selectedProvider, Dataset.TEAM, season);
            boolean primaryIdentityExists = team != null && team.getId() != null
                    && footballDataOwnedTeamIds.contains(team.getId());
            boolean allowedFallback = dataSourcePolicy.mayFallbackForCore(
                    selectedProvider, Dataset.TEAM, season, primaryIdentityExists);
            boolean canWriteCore = primaryOwner || allowedFallback;

            if (team == null) {
                if (!canWriteCore) {
                    log.debug("Skipping team '{}' from {} because primary identity exists or provider is not owner",
                            source.name(), selectedProvider);
                    continue;
                }
                team = new Team();
            }

            TeamExternalId existingMapping = team.getId() == null ? null : mappingByTeamId.get(team.getId());
            String pendingExternalId = pendingIdentityByTeam.get(team);
            if (existingMapping != null && !source.externalId().equals(existingMapping.getExternalId())) {
                log.warn("Skipping team '{}' from {} because canonical team {} is already mapped to provider ID {}",
                        source.name(), selectedProvider, team.getId(), existingMapping.getExternalId());
                continue;
            }
            if (pendingExternalId != null && !pendingExternalId.equals(source.externalId())) {
                log.warn("Skipping duplicate canonical team mapping in provider batch: team={}, existingExternalId={}, incomingExternalId={}",
                        team.getName(), pendingExternalId, source.externalId());
                continue;
            }

            if (canWriteCore && (selectedProvider == Provider.FOOTBALL_DATA
                    || selectedProvider == Provider.UNDER_STATS
                    || selectedProvider == Provider.FOOTBALL_DATA_UK)) {
                if (team.getName() == null || team.getName().isBlank()) team.setName(standardizedSource.name());
                if (team.getShortName() == null || team.getShortName().isBlank()) {
                    team.setShortName(standardizedSource.shortName());
                }
                if (team.getSlug() == null || team.getSlug().isBlank()) {
                    team.setSlug(slug(standardizedSource.name()) + "-" + selectedProvider.name().toLowerCase()
                            + "-" + standardizedSource.externalId());
                }
                if (standardizedSource.logoUrl() != null && !standardizedSource.logoUrl().isBlank()) team.setLogoUrl(standardizedSource.logoUrl());
                if (standardizedSource.foundedYear() != null) team.setFoundedYear(standardizedSource.foundedYear());
                team.setIsActive(true);
                if (standardizedSource.stadiumName() != null) team.setStadiumName(standardizedSource.stadiumName());
                if (standardizedSource.stadiumCapacity() != null) team.setStadiumCapacity(standardizedSource.stadiumCapacity());
                teamsToSave.add(team);
            }

            if (existingMapping == null && pendingExternalId == null) {
                pendingIdentityByTeam.put(team, source.externalId());
            }
            teamsByExternalId.put(source.externalId(), team);
            if (team.getId() != null && pendingIdentityByTeam.containsKey(team)) {
                mappingByTeamId.put(team.getId(), TeamExternalId.builder()
                        .team(team).provider(selectedProvider)
                        .externalId(pendingIdentityByTeam.get(team)).build());
            }
            synced++;
        }

        // Flush canonical teams first, then create all provider identities in one batch.
        if (!teamsToSave.isEmpty()) teamRepository.saveAll(teamsToSave);
        List<TeamExternalId> newMappings = pendingIdentityByTeam.entrySet().stream()
                .filter(entry -> entry.getKey().getId() != null)
                .map(entry -> TeamExternalId.builder().team(entry.getKey())
                        .provider(selectedProvider).externalId(entry.getValue()).build())
                .toList();
        if (!newMappings.isEmpty()) teamExternalIdRepository.saveAll(newMappings);

        log.info("Team persistence via {} completed. {} records saved/updated in {} team rows and {} identity rows.",
                selectedProvider, synced, teamsToSave.size(), newMappings.size());
    }

    private static Map<String, List<Team>> indexCanonicalTeams(List<Team> teams) {
        Map<String, List<Team>> index = new HashMap<>();
        for (Team team : teams) addTeamToNameIndex(index, team);
        return index;
    }

    private static void addTeamToNameIndex(Map<String, List<Team>> index, Team team) {
        if (team == null || team.getId() == null) return;
        // Only the canonical full name is safe for identity matching.
        // Short names are presentation/provider hints and can collide across clubs
        // (for example, one provider's short name may equal another club's full name).
        addNameKey(index, team.getName(), team);
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
        String key = MmoProviderDataStandardizer.teamNameMatchKey(source.name());
        if (key == null || key.isBlank()) return matches;

        for (Team candidate : index.getOrDefault(key, List.of())) {
            if (matchedIds.add(candidate.getId())) matches.add(candidate);
        }
        return matches;
    }

    private static String slug(String value) {
        if (value == null) return "unknown";
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
