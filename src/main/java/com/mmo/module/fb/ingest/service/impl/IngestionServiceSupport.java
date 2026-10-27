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
        if (provider == Provider.SOFA_SCORE) {
            Map<Long, Season> mapped = new LinkedHashMap<>();
            for (SeasonExternalId mapping : seasonExternalIdRepository.findAllByProvider(provider)) {
                Season season = mapping.getSeason();
                if (season != null && season.getId() != null && season.getLeague() != null
                        && Boolean.TRUE.equals(season.getLeague().getIsActive()) && season.getYear() != null) {
                    mapped.putIfAbsent(season.getId(), season);
                }
            }
            return List.copyOf(mapped.values());
        }
        return seasonRepository.findAllForActiveLeagues();
    }

    public List<Season> standingsSeasonsToSync(Provider provider) {
        return seasonRepository.findAllForActiveLeagues();
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
