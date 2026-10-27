package com.mmo.module.fb.ingest.service.impl;

import com.mmo.module.fb.league.entity.League;
import com.mmo.module.fb.league.entity.LeagueExternalId;
import com.mmo.module.fb.league.entity.Season;
import com.mmo.module.fb.league.entity.SeasonExternalId;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.league.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.league.repository.LeagueRepository;
import com.mmo.module.fb.league.repository.SeasonExternalIdRepository;
import com.mmo.module.fb.league.repository.SeasonRepository;
import com.mmo.module.fb.ingest.service.ExternalIdMappingService;
import com.mmo.module.fb.ingest.provider.model.ProviderLeague;
import com.mmo.module.fb.ingest.provider.model.ProviderSeason;
import com.mmo.module.fb.ingest.service.LeaguePersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaguePersistenceServiceImpl implements LeaguePersistenceService {

    private final LeagueRepository leagueRepository;
    private final SeasonRepository seasonRepository;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final SeasonExternalIdRepository seasonExternalIdRepository;
    private final ExternalIdMappingService externalIdMappingService;

    /**
     * Ensures league and season external IDs exist for a provider, even when another provider
     * is configured for the league ingestion schedule. Downstream resources depend on these IDs.
     */
    @Override
    @Transactional
    public void persistLeagues(Provider selectedProvider, List<ProviderLeague> sources) {
        int synced = 0;
        List<League> knownLeagues = new ArrayList<>(leagueRepository.findAll());
        for (ProviderLeague source : sources) {
            if (source == null || source.externalId() == null || source.externalId().isBlank()
                    || source.name() == null || source.name().isBlank()) {
                log.warn("Skipping league with empty external ID from {}", selectedProvider);
                continue;
            }

            League league = leagueExternalIdRepository
                    .findByProviderAndExternalId(selectedProvider, source.externalId())
                    .map(LeagueExternalId::getLeague)
                    .orElse(null);

            if (league == null) {
                List<League> candidates = knownLeagues.stream()
                        .filter(existing -> normalizeName(existing.getName()).equals(normalizeName(source.name())))
                        .toList();
                if (candidates.size() > 1 && source.country() != null && !source.country().isBlank()) {
                    List<League> countryMatches = candidates.stream()
                            .filter(existing -> existing.getCountry() != null
                                    && normalizeName(existing.getCountry()).equals(normalizeName(source.country())))
                            .toList();
                    if (!countryMatches.isEmpty()) candidates = countryMatches;
                }
                if (candidates.size() > 1) {
                    log.warn("Ambiguous league mapping for provider {} external ID {} name '{}'; skipping",
                            selectedProvider, source.externalId(), source.name());
                    continue;
                }
                if (candidates.isEmpty() && selectedProvider != Provider.FOOTBALL_DATA) {
                    log.info("Skipping provider league '{}' (external ID {}) because no existing canonical league matched; secondary provider mapping sync does not create core entities.",
                            source.name(), source.externalId());
                    continue;
                }
                league = candidates.isEmpty() ? new League() : candidates.get(0);
                if (candidates.isEmpty()) league.setIsActive(false);
            }

            LeagueExternalId existingLeagueMapping = league.getId() == null ? null
                    : leagueExternalIdRepository.findByLeagueIdAndProvider(league.getId(), selectedProvider).orElse(null);
            if (existingLeagueMapping != null && !source.externalId().equals(existingLeagueMapping.getExternalId())) {
                log.warn("Skipping league '{}' from {} because canonical league {} is already mapped to provider ID {}",
                        source.name(), selectedProvider, league.getId(), existingLeagueMapping.getExternalId());
                continue;
            }

            if (selectedProvider == Provider.FOOTBALL_DATA) {
                if (league.getName() == null || league.getName().isBlank()) league.setName(source.name());
                if (league.getSlug() == null || league.getSlug().isBlank()) {
                    league.setSlug(slug(source.name()) + "-" + selectedProvider.name().toLowerCase()
                            + "-" + source.externalId());
                }
                if (source.country() != null && !source.country().isBlank()) league.setCountry(source.country());
                if (source.logoUrl() != null && !source.logoUrl().isBlank()) league.setLogoUrl(source.logoUrl());
                league = leagueRepository.save(league);
                Long savedLeagueId = league.getId();
                if (knownLeagues.stream().noneMatch(existing -> existing.getId() != null
                        && existing.getId().equals(savedLeagueId))) {
                    knownLeagues.add(league);
                }
            }
            League savedLeague = league;
            externalIdMappingService.registerLeague(savedLeague, selectedProvider, source.externalId());

            for (ProviderSeason sourceSeason : source.seasons() == null ? List.<ProviderSeason>of() : source.seasons()) {
                if (sourceSeason == null || sourceSeason.externalId() == null || sourceSeason.externalId().isBlank()
                        || sourceSeason.year() == null || sourceSeason.year().isBlank()) {
                    log.warn("Skipping season without provider ID/year for league {} from {}",
                            source.name(), selectedProvider);
                    continue;
                }

                Season season = seasonExternalIdRepository
                        .findByProviderAndExternalId(selectedProvider, sourceSeason.externalId())
                        .map(SeasonExternalId::getSeason)
                        .orElse(null);
                if (season != null && (season.getLeague() == null || season.getLeague().getId() == null
                        || !season.getLeague().getId().equals(savedLeague.getId())
                        || !sourceSeason.year().equals(season.getYear()))) {
                    log.warn("Provider {} season ID {} is already mapped to a different canonical season; skipping",
                            selectedProvider, sourceSeason.externalId());
                    continue;
                }
                if (season == null) {
                    season = seasonRepository.findByLeague_IdAndYear(savedLeague.getId(), sourceSeason.year())
                            .orElse(null);
                    if (season == null && selectedProvider != Provider.FOOTBALL_DATA) {
                        log.debug("Skipping provider season {} for canonical league {} because no core season exists",
                                sourceSeason.year(), savedLeague.getId());
                        continue;
                    }
                    if (season == null) {
                        season = Season.builder().league(savedLeague).year(sourceSeason.year()).build();
                    }
                }
                SeasonExternalId existingSeasonMapping = season.getId() == null ? null
                        : seasonExternalIdRepository.findBySeasonIdAndProvider(season.getId(), selectedProvider).orElse(null);
                if (existingSeasonMapping != null
                        && !sourceSeason.externalId().equals(existingSeasonMapping.getExternalId())) {
                    log.warn("Skipping season {} from {} because canonical season {} is already mapped to provider ID {}",
                            sourceSeason.year(), selectedProvider, season.getId(), existingSeasonMapping.getExternalId());
                    continue;
                }

                if (selectedProvider == Provider.FOOTBALL_DATA) {
                    season.setCurrent(sourceSeason.current());
                    season.setStartDate(sourceSeason.startDate());
                    season.setEndDate(sourceSeason.endDate());
                    season = seasonRepository.save(season);
                }
                externalIdMappingService.registerSeason(season, selectedProvider, sourceSeason.externalId());
            }
            synced++;
        }
        log.info("League ingestion completed. {} leagues processed.", synced);
    }

    private static String normalizeName(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private static String slug(String value) {
        if (value == null) return "unknown";
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
