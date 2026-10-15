package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.domain.entity.League;
import com.mmo.module.fb.domain.entity.LeagueExternalId;
import com.mmo.module.fb.domain.entity.Season;
import com.mmo.module.fb.domain.entity.SeasonExternalId;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.repository.LeagueRepository;
import com.mmo.module.fb.domain.repository.SeasonExternalIdRepository;
import com.mmo.module.fb.domain.repository.SeasonRepository;
import com.mmo.module.fb.ingest.provider.model.ProviderLeague;
import com.mmo.module.fb.ingest.provider.model.ProviderSeason;
import com.mmo.module.fb.ingest.provider.spi.ProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeagueIngestionServiceImpl implements com.mmo.module.fb.ingest.service.LeagueIngestionService {

    private final ProviderRegistry providerRegistry;
    private final AppProperties appProperties;
    private final LeagueRepository leagueRepository;
    private final SeasonRepository seasonRepository;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final SeasonExternalIdRepository seasonExternalIdRepository;

    public void syncLeagues() {
        syncLeaguesForProvider(appProperties.getIngestion().getProviders().getLeague());
    }

    /**
     * Ensures league and season external IDs exist for a provider, even when another provider
     * is configured for the league ingestion schedule. Downstream resources depend on these IDs.
     */
    public void syncLeaguesForProvider(Provider selectedProvider) {
        if (selectedProvider == Provider.API_FOOTBALL
                && (appProperties.getApiFootball() == null || !appProperties.getApiFootball().isEnabled())) {
            return;
        }
        List<Long> configuredIds = selectedProvider == Provider.API_FOOTBALL
                ? appProperties.getApiFootball().getLeagueIds() : List.of();
        if (selectedProvider == Provider.API_FOOTBALL
                && (configuredIds == null || configuredIds.isEmpty())) {
            log.warn("No API-Football league IDs configured; set API_FOOTBALL_LEAGUE_IDS.");
            return;
        }

        int synced = 0;
        List<League> knownActiveLeagues = new ArrayList<>(leagueRepository.findByIsActive(true));
        for (ProviderLeague source : providerRegistry.league(selectedProvider).fetchLeagues(configuredIds)) {
            League league = leagueExternalIdRepository
                    .findByProviderAndExternalId(selectedProvider, source.externalId())
                    .map(LeagueExternalId::getLeague)
                    .orElse(null);

            if (league == null) {
                List<League> candidates = knownActiveLeagues.stream()
                        .filter(existing -> normalizeName(existing.getName()).equals(normalizeName(source.name())))
                        .toList();
                if (candidates.size() > 1 && source.country() != null && !source.country().isBlank()) {
                    List<League> countryMatches = candidates.stream()
                            .filter(existing -> existing.getCountry() != null
                                    && normalizeName(existing.getCountry()).equals(normalizeName(source.country())))
                            .toList();
                    if (!countryMatches.isEmpty()) {
                        candidates = countryMatches;
                    }
                }
                if (candidates.size() > 1) {
                    log.warn("Ambiguous league mapping for provider {} external ID {} name '{}'; skipping",
                            selectedProvider, source.externalId(), source.name());
                    continue;
                }
                league = candidates.isEmpty() ? new League() : candidates.get(0);
            }

            if (league.getName() == null || league.getName().isBlank()) {
                league.setName(source.name());
            }
            if (league.getSlug() == null || league.getSlug().isBlank()) {
                league.setSlug(slug(source.name()) + "-" + selectedProvider.name().toLowerCase()
                        + "-" + source.externalId());
            }
            if (source.country() != null && !source.country().isBlank()) {
                league.setCountry(source.country());
            }
            if (source.logoUrl() != null && !source.logoUrl().isBlank()) {
                league.setLogoUrl(source.logoUrl());
            }
            if (league.getExternalId() == null || league.getExternalId().isBlank()) {
                league.setExternalId(source.externalId());
            }
            league.setActive(true);
            league.setIsActive(true);
            league = leagueRepository.save(league);
            League savedLeague = league;
            Long savedLeagueId = savedLeague.getId();
            if (knownActiveLeagues.stream().noneMatch(existing -> existing.getId().equals(savedLeagueId))) {
                knownActiveLeagues.add(savedLeague);
            }


            LeagueExternalId external = leagueExternalIdRepository
                    .findByLeagueIdAndProvider(savedLeague.getId(), selectedProvider)
                    .orElseGet(() -> LeagueExternalId.builder()
                            .league(savedLeague).provider(selectedProvider)
                            .externalId(source.externalId()).build());
            external.setExternalId(source.externalId());
            leagueExternalIdRepository.save(external);

            for (ProviderSeason sourceSeason : source.seasons()) {
                if (sourceSeason.externalId() == null || sourceSeason.externalId().isBlank()
                        || sourceSeason.year() == null || sourceSeason.year().isBlank()) {
                    log.warn("Skipping season without provider ID/year for league {} from {}",
                            source.name(), selectedProvider);
                    continue;
                }

                Season season = seasonExternalIdRepository
                        .findByProviderAndExternalId(selectedProvider, sourceSeason.externalId())
                        .map(SeasonExternalId::getSeason)
                        .orElse(null);
                if (season != null && (!season.getLeague().getId().equals(savedLeague.getId())
                        || !season.getYear().equals(sourceSeason.year()))) {
                    log.warn("Provider {} season ID {} is already mapped to a different canonical season; skipping",
                            selectedProvider, sourceSeason.externalId());
                    continue;
                }
                if (season == null) {
                    season = seasonRepository.findByLeague_IdAndYear(savedLeague.getId(), sourceSeason.year())
                            .orElseGet(() -> Season.builder()
                                    .league(savedLeague).year(sourceSeason.year()).build());
                }
                season.setCurrent(sourceSeason.current());
                season.setStartDate(sourceSeason.startDate());
                season.setEndDate(sourceSeason.endDate());
                season.setExternalId(sourceSeason.year());
                season = seasonRepository.save(season);

                Season savedSeason = season;
                SeasonExternalId seasonExternal = seasonExternalIdRepository
                        .findBySeasonIdAndProvider(savedSeason.getId(), selectedProvider)
                        .orElseGet(() -> SeasonExternalId.builder()
                                .season(savedSeason).provider(selectedProvider)
                                .externalId(sourceSeason.externalId()).build());
                seasonExternal.setExternalId(sourceSeason.externalId());
                seasonExternalIdRepository.save(seasonExternal);
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
