package com.mmo.module.fb.ingest.service.impl;

import com.mmo.module.fb.ingest.enums.Provider;
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
        Map<String, Team> teamsByExternalId = new HashMap<>();
        Map<Long, TeamExternalId> mappingByTeamId = new HashMap<>();

        teamExternalIdRepository.findAllByProvider(selectedProvider).forEach(mapping -> {
            teamsByExternalId.put(mapping.getExternalId(), mapping.getTeam());
            mappingByTeamId.put(mapping.getTeam().getId(), mapping);
        });

        for (ProviderTeam source : teams == null ? List.<ProviderTeam>of() : teams) {
            if (source == null || source.externalId() == null || source.externalId().isBlank()
                    || source.name() == null || source.name().isBlank()) {
                log.warn("Skipping team with empty external ID/name from {}", selectedProvider);
                continue;
            }

            Team team = teamsByExternalId.get(source.externalId());
            if (team == null && selectedProvider != Provider.FOOTBALL_DATA
                    && selectedProvider != Provider.UNDER_STATS) {
                log.debug("Skipping unmapped team '{}' from {} (external ID {}). "
                                + "Register its provider ID against the canonical MMO team before syncing.",
                        source.name(), selectedProvider, source.externalId());
                continue;
            }
            if (team == null) {
                // FOOTBALL_DATA is the canonical source for core team creation. Secondary providers
                // must use explicit external-ID mappings; display-name similarity is not identity.
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
            synced++;
        }
        log.info("Team persistence via {} completed. {} records saved/updated.", selectedProvider, synced);
    }

    private static String slug(String value) {
        if (value == null) return "unknown";
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
