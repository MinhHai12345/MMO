package com.mmo.module.fb.ingest.service.impl;

import com.mmo.module.fb.league.entity.Season;
import com.mmo.module.fb.team.entity.Team;
import com.mmo.module.fb.team.entity.TeamExternalId;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.team.repository.TeamExternalIdRepository;
import com.mmo.module.fb.team.repository.TeamRepository;
import com.mmo.module.fb.ingest.service.ExternalIdMappingService;
import com.mmo.module.fb.ingest.provider.model.ProviderTeam;
import com.mmo.module.fb.ingest.service.TeamPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        List<Team> knownTeams = new ArrayList<>(teamRepository.findAll());
        Map<String, Team> teamsByExternalId = new HashMap<>();
        Map<Long, TeamExternalId> mappingByTeamId = new HashMap<>();
        teamExternalIdRepository.findAllByProvider(selectedProvider).forEach(mapping -> {
            teamsByExternalId.put(mapping.getExternalId(), mapping.getTeam());
            mappingByTeamId.put(mapping.getTeam().getId(), mapping);
        });

        for (ProviderTeam source : teams) {
            if (source == null || source.externalId() == null || source.externalId().isBlank()
                    || source.name() == null || source.name().isBlank()) {
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

            TeamExternalId existingMapping = team.getId() == null ? null : mappingByTeamId.get(team.getId());
            if (existingMapping != null && !source.externalId().equals(existingMapping.getExternalId())) {
                log.warn("Skipping team '{}' from {} because canonical team {} is already mapped to provider ID {}",
                        source.name(), selectedProvider, team.getId(), existingMapping.getExternalId());
                continue;
            }

            if (team.getName() == null || team.getName().isBlank()) team.setName(source.name());
            if (team.getShortName() == null || team.getShortName().isBlank()) team.setShortName(source.shortName());
            if (team.getSlug() == null || team.getSlug().isBlank()) {
                team.setSlug(slug(source.name()) + "-" + selectedProvider.name().toLowerCase()
                        + "-" + source.externalId());
            }
            team.setLogoUrl(source.logoUrl());
            team.setFoundedYear(source.foundedYear());
            team.setIsActive(true);
            if (source.stadiumName() != null || source.stadiumCapacity() != null) {
                team.setStadiumName(source.stadiumName());
                team.setStadiumCapacity(source.stadiumCapacity());
            }
            team = teamRepository.save(team);

            Team savedTeam = team;
            externalIdMappingService.registerTeam(savedTeam, selectedProvider, source.externalId());
            TeamExternalId mapping = teamExternalIdRepository
                    .findByTeamIdAndProvider(savedTeam.getId(), selectedProvider).orElseThrow();
            teamsByExternalId.put(source.externalId(), savedTeam);
            mappingByTeamId.put(savedTeam.getId(), mapping);
            if (knownTeams.stream().noneMatch(existing -> existing.getId() != null
                    && existing.getId().equals(savedTeam.getId()))) {
                knownTeams.add(savedTeam);
            }
            synced++;
        }
        log.info("Team persistence via {} completed. {} records saved/updated.", selectedProvider, synced);
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
