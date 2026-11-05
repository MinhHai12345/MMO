package com.mmo.module.fb.ingest.application.support;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.league.entity.SeasonExternalId;
import com.mmo.module.fb.domain.league.repository.SeasonExternalIdRepository;
import com.mmo.module.fb.domain.league.repository.SeasonRepository;
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
    private final DataSourcePolicy dataSourcePolicy;

    public void requireProviderEnabled(Provider provider, String resource) {
        if (provider == null) {
            throw new IllegalArgumentException("Provider must not be null for " + resource + " ingestion");
        }

        AppProperties.ProviderConfig config = providerConfig(provider);
        if (config == null || !config.isEnabled()) {
            throw new IllegalStateException(provider + " is disabled or not configured but was selected for "
                                            + resource + " ingestion");
        }
    }

    private AppProperties.ProviderConfig providerConfig(Provider provider) {
        if (provider == null || properties.getIngestion() == null
            || properties.getIngestion().getProviderConfig() == null) {
            return null;
        }
        return properties.getIngestion().getProviderConfig().get(provider);
    }

    public List<Season> seasonsToSync(Provider provider) {
        return seasonsToSync(provider, Dataset.FIXTURE);
    }

    public List<Season> seasonsToSync(Provider provider, Dataset dataset) {
        Map<Long, Season> mapped = new LinkedHashMap<>();
        for (SeasonExternalId mapping : seasonExternalIdRepository.findAllByProvider(provider)) {
            Season season = mapping.getSeason();
            if (season != null && season.isActive() && season.getYear() != null
                && season.getLeague() != null && Boolean.TRUE.equals(season.getLeague().getIsActive())
                && dataSourcePolicy.isPrimary(provider, dataset, season)) {
                mapped.putIfAbsent(season.getId(), season);
            }
        }
        return List.copyOf(mapped.values());
    }

    /**
     * Historical scope used by initial backfills and coverage audits. Includes inactive
     * seasons that have a provider mapping and are owned by the selected provider.
     */
    public List<Season> allMappedSeasonsToSync(Provider provider) {
        return allMappedSeasonsToSync(provider, Dataset.FIXTURE);
    }

    public List<Season> allMappedSeasonsToSync(Provider provider, Dataset dataset) {
        Map<Long, Season> mapped = new LinkedHashMap<>();
        for (SeasonExternalId mapping : seasonExternalIdRepository.findAllByProvider(provider)) {
            Season season = mapping.getSeason();
            if (season != null && season.getId() != null && season.getYear() != null
                    && season.getLeague() != null
                    && Boolean.TRUE.equals(season.getLeague().getIsActive())
                    && dataSourcePolicy.isPrimary(provider, dataset, season)) {
                mapped.putIfAbsent(season.getId(), season);
            }
        }
        return List.copyOf(mapped.values());
    }

    public boolean hasAnySeasons(boolean includeHistorical) {
        return seasonRepository.findAllForActiveLeagues().stream()
                .anyMatch(season -> includeHistorical || season.isActive());
    }

    public boolean hasAnySeasonOwnedBy(Provider provider, Dataset dataset, boolean includeHistorical) {
        return seasonRepository.findAllForActiveLeagues().stream()
                .filter(season -> includeHistorical || season.isActive())
                .anyMatch(season -> dataSourcePolicy.isPrimary(provider, dataset, season));
    }

    /**
     * Standings must be backfilled for every mapped season, not only active seasons.
     * A provider may not expose standings for every season; callers report NOT_AVAILABLE
     * without deleting or overwriting previously persisted rows.
     */
    public List<Season> standingsSeasonsToSync(Provider provider) {
        return allMappedSeasonsToSync(provider, Dataset.STANDINGS);
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
