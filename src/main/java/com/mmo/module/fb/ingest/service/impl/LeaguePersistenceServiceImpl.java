package com.mmo.module.fb.ingest.service.impl;

import com.mmo.module.fb.league.entity.League;
import com.mmo.module.fb.league.entity.LeagueExternalId;
import com.mmo.module.fb.league.entity.Season;
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
import org.springframework.util.StringUtils;

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
     * Provider IDs identify mapping rows; provider-specific identify values are retained
     * separately for constructing that provider's fetch requests. Names are never used
     * to infer that two providers refer to the same canonical league.
     */
    @Override
    @Transactional
    public void persistLeagues(Provider selectedProvider, List<ProviderLeague> sources) {
        int synced = 0;
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

            if (league == null && selectedProvider == Provider.UNDER_STATS) {
                // UnderStat is a catalogue source: its /getStatData endpoint is the source
                // of league identity, so exact unique-name matching is allowed here.
                List<League> named = leagueRepository.findAllByNameIgnoreCase(source.name());
                league = named.size() == 1 ? named.get(0) : null;
                if (league != null && leagueExternalIdRepository
                        .findByLeagueIdAndProvider(league.getId(), selectedProvider).isPresent()) {
                    league = null;
                }
            }
            if (league == null && selectedProvider != Provider.FOOTBALL_DATA
                    && selectedProvider != Provider.UNDER_STATS) {
                log.debug("Skipping unmapped league '{}' from {} (external ID {}). Register its provider ID "
                                + "against the canonical league before syncing secondary-provider data.",
                        source.name(), selectedProvider, source.externalId());
                continue;
            }
            if (league == null) {
                league = new League();
                league.setIsActive(false);
            }

            LeagueExternalId existingLeagueMapping = league.getId() == null ? null
                    : leagueExternalIdRepository.findByLeagueIdAndProvider(league.getId(), selectedProvider)
                    .orElse(null);
            if (existingLeagueMapping != null
                    && !source.externalId().equals(existingLeagueMapping.getExternalId())) {
                log.warn("Skipping league '{}' from {} because canonical league {} is already mapped to provider ID {}",
                        source.name(), selectedProvider, league.getId(), existingLeagueMapping.getExternalId());
                continue;
            }

            if (selectedProvider == Provider.FOOTBALL_DATA || selectedProvider == Provider.UNDER_STATS) {
                if (league.getName() == null || league.getName().isBlank()) league.setName(source.name());
                if (league.getSlug() == null || league.getSlug().isBlank()) {
                    league.setSlug(slug(source.name()));
                }
                if (source.country() != null && !source.country().isBlank()
                        && (selectedProvider == Provider.FOOTBALL_DATA || !StringUtils.hasText(league.getCountry()))) {
                    league.setCountry(source.country());
                }
                if (source.logoUrl() != null && !source.logoUrl().isBlank()) league.setLogoUrl(source.logoUrl());
                league = leagueRepository.save(league);
            }

            LeagueExternalId mapping = externalIdMappingService.registerLeague(
                    league, selectedProvider, source.externalId());
            mapping.setIdentify(source.identify());
            leagueExternalIdRepository.save(mapping);

            for (ProviderSeason sourceSeason : source.seasons() == null
                    ? List.<ProviderSeason>of() : source.seasons()) {
                if (sourceSeason == null || sourceSeason.externalId() == null || sourceSeason.externalId().isBlank()
                        || sourceSeason.year() == null || sourceSeason.year().isBlank()) {
                    log.warn("Skipping season without provider ID/year for league {} from {}",
                            source.name(), selectedProvider);
                    continue;
                }

                Season season = seasonExternalIdRepository
                        .findByProviderAndExternalId(selectedProvider, sourceSeason.externalId())
                        .map(com.mmo.module.fb.league.entity.SeasonExternalId::getSeason)
                        .orElse(null);
                if (season != null && (season.getLeague() == null || season.getLeague().getId() == null
                        || !season.getLeague().getId().equals(league.getId())
                        || !sourceSeason.year().equals(season.getYear()))) {
                    log.warn("Provider {} season ID {} is already mapped to a different canonical season; skipping",
                            selectedProvider, sourceSeason.externalId());
                    continue;
                }
                if (season == null) {
                    season = seasonRepository.findByLeague_IdAndYear(league.getId(), sourceSeason.year())
                            .orElse(null);
                    if (season == null && selectedProvider != Provider.FOOTBALL_DATA
                            && selectedProvider != Provider.UNDER_STATS) {
                        log.debug("Skipping provider season {} for canonical league {} because no core season exists",
                                sourceSeason.year(), league.getId());
                        continue;
                    }
                    if (season == null) {
                        season = Season.builder().league(league).year(sourceSeason.year()).build();
                    }
                }
                var existingSeasonMapping = season.getId() == null ? null
                        : seasonExternalIdRepository.findBySeasonIdAndProvider(season.getId(), selectedProvider)
                        .orElse(null);
                if (existingSeasonMapping != null
                        && !sourceSeason.externalId().equals(existingSeasonMapping.getExternalId())) {
                    log.warn("Skipping season {} from {} because canonical season {} is already mapped to provider ID {}",
                            sourceSeason.year(), selectedProvider, season.getId(), existingSeasonMapping.getExternalId());
                    continue;
                }

                if (selectedProvider == Provider.FOOTBALL_DATA || selectedProvider == Provider.UNDER_STATS) {
                    // Current seasons are active by default when first discovered. Preserve
                    // explicit operator choices on already-persisted seasons.
                    if (season.getId() == null) season.setActive(sourceSeason.current());
                    season.setCurrent(sourceSeason.current());
                    if (sourceSeason.startDate() != null) season.setStartDate(sourceSeason.startDate());
                    if (sourceSeason.endDate() != null) season.setEndDate(sourceSeason.endDate());
                    season = seasonRepository.save(season);
                }
                externalIdMappingService.registerSeason(season, selectedProvider, sourceSeason.externalId());
            }
            synced++;
        }
        log.info("League ingestion completed. {} leagues processed.", synced);
    }

    private static String slug(String value) {
        if (value == null) return "unknown";
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
