package com.mmo.module.fb.ingest.policy;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.league.entity.Season;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Central source-of-truth policy. Routing is based on logical dataset and the
 * season's start year, never on a global provider setting alone.
 *
 * <p>Football-Data UK owns historical Core (2014-2022). Football-Data API owns
 * Core from 2023 onward. UK remains the owner of historical match statistics,
 * xG and historical odds for every CSV season it covers.</p>
 */
@Component
public class DataSourcePolicy {

    public static final int UK_CORE_FROM = 2014;
    public static final int FOOTBALL_DATA_CORE_FROM = 2023;

    public ProviderRoute route(Dataset dataset, Season season) {
        return route(dataset, seasonStartYear(season));
    }

    public ProviderRoute route(Dataset dataset, String season) {
        return route(dataset, parseSeasonStartYear(season));
    }

    public ProviderRoute route(Dataset dataset, int startYear) {
        if (dataset == null) throw new IllegalArgumentException("Dataset must not be null");

        return switch (dataset) {
            case LEAGUE -> new ProviderRoute(Provider.FOOTBALL_DATA,
                    List.of(Provider.FOOTBALL_DATA_UK));
            case SEASON, TEAM, FIXTURE -> coreRoute(startYear);
            case STANDINGS -> startYear >= FOOTBALL_DATA_CORE_FROM
                    ? new ProviderRoute(Provider.FOOTBALL_DATA, List.of())
                    : new ProviderRoute(null, List.of());
            case MATCH_STATISTICS, MATCH_XG, HISTORICAL_ODDS ->
                    startYear >= UK_CORE_FROM
                            ? new ProviderRoute(Provider.FOOTBALL_DATA_UK, List.of())
                            : new ProviderRoute(null, List.of());
            case UPCOMING_ODDS -> new ProviderRoute(null, List.of());
        };
    }

    public Optional<Provider> primaryProvider(Dataset dataset, Season season) {
        return Optional.ofNullable(route(dataset, season).primary());
    }

    public boolean isPrimary(Provider provider, Dataset dataset, Season season) {
        return provider != null && route(dataset, season).primary() == provider;
    }

    public boolean isPrimary(Provider provider, Dataset dataset, String season) {
        return provider != null && route(dataset, season).primary() == provider;
    }

    public boolean isAllowed(Provider provider, Dataset dataset, Season season) {
        return route(dataset, season).allows(provider);
    }

    /**
     * A secondary source may fill an entity that has no primary-provider identity.
     * Once the primary provider has mapped that entity, the primary provider owns its
     * core fields and secondary updates must be restricted to their own datasets.
     */
    public boolean mayFallbackForCore(Provider provider, Dataset dataset, Season season,
                                       boolean primaryIdentityExists) {
        ProviderRoute route = route(dataset, season);
        return provider != null && route.secondary().contains(provider) && !primaryIdentityExists;
    }

    public static int seasonStartYear(Season season) {
        return season == null ? -1 : parseSeasonStartYear(season.getYear());
    }

    public static int parseSeasonStartYear(String value) {
        if (value == null || value.isBlank()) return -1;
        String digits = value.replaceAll("\\D", "");
        if (digits.length() < 4) return -1;
        try {
            return Integer.parseInt(digits.substring(0, 4));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static ProviderRoute coreRoute(int startYear) {
        if (startYear >= UK_CORE_FROM && startYear < FOOTBALL_DATA_CORE_FROM) {
            return new ProviderRoute(Provider.FOOTBALL_DATA_UK, List.of());
        }
        if (startYear >= FOOTBALL_DATA_CORE_FROM) {
            return new ProviderRoute(Provider.FOOTBALL_DATA, List.of(Provider.FOOTBALL_DATA_UK));
        }
        return new ProviderRoute(null, List.of());
    }
}
