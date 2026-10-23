package com.mmo.module.fb.ingest.provider.spi;

import com.mmo.module.fb.ingest.enums.Provider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Typed registry of ingestion strategies.
 *
 * Each resource has its own contract, so callers cannot accidentally invoke
 * a team strategy where a fixture strategy is required.
 */
@Component
public class ProviderRegistry {

    private final Map<Provider, LeagueProvider> leagueProviders;
    private final Map<Provider, TeamProvider> teamProviders;
    private final Map<Provider, FixtureProvider> fixtureProviders;
    private final Map<Provider, StandingsProvider> standingsProviders;
    private final Map<Provider, OddsProvider> oddsProviders;

    public ProviderRegistry(
            List<LeagueProvider> leagueProviders,
            List<TeamProvider> teamProviders,
            List<FixtureProvider> fixtureProviders,
            List<StandingsProvider> standingsProviders,
            List<OddsProvider> oddsProviders) {
        this.leagueProviders = index(leagueProviders, LeagueProvider::getProvider, "LeagueProvider");
        this.teamProviders = index(teamProviders, TeamProvider::getProvider, "TeamProvider");
        this.fixtureProviders = index(fixtureProviders, FixtureProvider::getProvider, "FixtureProvider");
        this.standingsProviders = index(standingsProviders, StandingsProvider::getProvider, "StandingsProvider");
        this.oddsProviders = index(oddsProviders, OddsProvider::getProvider, "OddsProvider");
    }

    public LeagueProvider league(Provider provider) {
        return get(leagueProviders, provider, "LeagueProvider");
    }

    public TeamProvider team(Provider provider) {
        return get(teamProviders, provider, "TeamProvider");
    }

    public FixtureProvider fixture(Provider provider) {
        return get(fixtureProviders, provider, "FixtureProvider");
    }

    public StandingsProvider standings(Provider provider) {
        return get(standingsProviders, provider, "StandingsProvider");
    }

    public OddsProvider odds(Provider provider) {
        return get(oddsProviders, provider, "OddsProvider");
    }

    private static <T> Map<Provider, T> index(
            List<T> providers,
            Function<T, Provider> providerCode,
            String resourceName) {
        Map<Provider, T> indexed = new EnumMap<>(Provider.class);
        for (T strategy : providers) {
            if (strategy == null) {
                continue;
            }
            Provider code = providerCode.apply(strategy);
            if (code == null) {
                throw new IllegalStateException(resourceName + " returned a null provider code");
            }
            T previous = indexed.putIfAbsent(code, strategy);
            if (previous != null) {
                throw new IllegalStateException(
                        "Multiple " + resourceName + " strategies configured for provider " + code);
            }
        }
        return Map.copyOf(indexed);
    }

    private static <T> T get(Map<Provider, T> strategies, Provider provider, String resourceName) {
        if (provider == null) {
            throw new IllegalArgumentException("Selected provider must not be null for " + resourceName);
        }
        T strategy = strategies.get(provider);
        if (strategy == null) {
            throw new IllegalStateException(
                    "No " + resourceName + " configured for provider " + provider);
        }
        return strategy;
    }
}
