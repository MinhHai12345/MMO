package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.league.entity.Season;
import com.mmo.module.fb.league.entity.SeasonExternalId;
import com.mmo.module.fb.league.repository.SeasonExternalIdRepository;
import com.mmo.module.fb.league.repository.SeasonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared mechanics for resource ingestion. Keeps provider validation and season selection
 * consistent without moving orchestration or persistence out of the resource services.
 */
@Component
@RequiredArgsConstructor
public class IngestionServiceSupport {

    private final AppProperties properties;
    private final SeasonRepository seasonRepository;
    private final SeasonExternalIdRepository seasonExternalIdRepository;

    public void requireProviderEnabled(Provider provider, String resource) {
        if (provider == null) {
            throw new IllegalArgumentException("Provider must not be null for " + resource + " ingestion");
        }

        AppProperties.ProviderConfig config = providerConfig(provider);
        boolean enabled = config != null ? config.isEnabled() : switch (provider) {
            case FOOTBALL_DATA -> properties.getFootballData() != null
                    && properties.getFootballData().isEnabled();
            case API_FOOTBALL -> properties.getApiFootball() != null
                    && properties.getApiFootball().isEnabled();
            case SOFA_SCORE -> properties.getSofaScore() != null && properties.getSofaScore().isEnabled();
            case UNDER_STATS -> properties.getUnderstats() != null && properties.getUnderstats().isEnabled();
        };

        if (!enabled) {
            throw new IllegalStateException(provider + " is disabled but was selected for " + resource + " ingestion");
        }
    }

    public List<Long> configuredLeagueIds(Provider provider) {
        AppProperties.ProviderConfig config = providerConfig(provider);
        if (config != null && config.getLeagueIds() != null && !config.getLeagueIds().isEmpty()) {
            return config.getLeagueIds().stream().filter(id -> id != null).distinct().toList();
        }

        return switch (provider) {
            case API_FOOTBALL -> properties.getApiFootball() == null
                    || properties.getApiFootball().getLeagueIds() == null
                    ? List.of() : properties.getApiFootball().getLeagueIds();
            case FOOTBALL_DATA -> properties.getFootballData() == null
                    || properties.getFootballData().getCompetitionIds() == null
                    ? List.of() : properties.getFootballData().getCompetitionIds();
            case SOFA_SCORE -> properties.getSync() == null || properties.getSync().getTournamentIds() == null
                    ? List.of() : properties.getSync().getTournamentIds().stream().sorted().toList();
            case UNDER_STATS -> properties.getUnderstats() == null
                    || properties.getUnderstats().getLeagueIds() == null
                    ? List.of() : properties.getUnderstats().getLeagueIds();
        };
    }

    public List<String> configuredSeasonYears(Provider provider) {
        // SofaScore seasons are external IDs, not calendar-year values. Use the existing
        // external-season mapping path for SofaScore rather than interpreting IDs as years.
        if (provider == Provider.SOFA_SCORE) return List.of();

        AppProperties.ProviderConfig config = providerConfig(provider);
        List<String> configured = config == null ? null : config.getSeasons();
        if (configured == null || configured.isEmpty()) {
            configured = switch (provider) {
                case FOOTBALL_DATA -> properties.getFootballData() == null
                        ? List.of() : properties.getFootballData().getSeasons();
                case API_FOOTBALL -> properties.getApiFootball() == null
                        ? List.of() : properties.getApiFootball().getSeasons();
                case UNDER_STATS -> properties.getUnderstats() == null
                        ? List.of() : properties.getUnderstats().getSeasons();
                case SOFA_SCORE -> List.of();
            };
        }
        if (configured == null) return List.of();
        return configured.stream()
                .filter(year -> year != null && !year.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private AppProperties.ProviderConfig providerConfig(Provider provider) {
        if (provider == null || properties.getIngestion() == null
                || properties.getIngestion().getProviderConfig() == null) {
            return null;
        }
        return properties.getIngestion().getProviderConfig().get(provider);
    }

    public List<Season> seasonsToSync(Provider provider) {
        if (provider == Provider.SOFA_SCORE) {
            Map<Long, Season> mapped = new LinkedHashMap<>();
            for (SeasonExternalId mapping : seasonExternalIdRepository.findAllByProvider(provider)) {
                Season season = mapping.getSeason();
                if (season != null && season.getId() != null && season.getLeague() != null
                        && season.getYear() != null) {
                    mapped.putIfAbsent(season.getId(), season);
                }
            }
            return List.copyOf(mapped.values());
        }

        List<String> years = configuredSeasonYears(provider);
        if (!years.isEmpty()) {
            return seasonRepository.findByYearInAndLeagueIsNotNullAndYearIsNotNull(years);
        }
        return seasonRepository.findByIsCurrentTrueAndLeagueIsNotNullAndYearIsNotNull();
    }

    public List<Season> standingsSeasonsToSync(Provider provider) {
        List<String> years = configuredSeasonYears(provider);
        return years.isEmpty()
                ? seasonRepository.findByIsCurrentTrueAndLeagueIsNotNullAndYearIsNotNull()
                : seasonRepository.findByYearInAndLeagueIsNotNullAndYearIsNotNull(years);
    }

    public static Long parseExternalId(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
