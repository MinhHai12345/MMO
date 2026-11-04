package com.mmo.module.fb.ingest.provider.spi;

import com.mmo.module.fb.ingest.enums.Provider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Typed provider registry. Each data capability has its own contract so a resource
 * ingestion service cannot accidentally resolve a provider for a different resource.
 */
@Component
public class ProviderRegistry {

    private final Map<Provider, LeagueProvider> leagueProviders;
    private final Map<Provider, SeasonProvider> seasonProviders;
    private final Map<Provider, TeamProvider> teamProviders;
    private final Map<Provider, FixtureProvider> fixtureProviders;
    private final Map<Provider, StandingsProvider> standingsProviders;
    private final Map<Provider, OddsProvider> oddsProviders;
    private final Map<Provider, HistoricalStatsProvider> historicalStatsProviders;
    private final Map<Provider, MatchStatisticsProvider> matchStatisticsProviders;

    @Autowired
    public ProviderRegistry(
            List<LeagueProvider> leagueProviders,
            List<SeasonProvider> seasonProviders,
            List<TeamProvider> teamProviders,
            List<FixtureProvider> fixtureProviders,
            List<StandingsProvider> standingsProviders,
            List<OddsProvider> oddsProviders,
            List<HistoricalStatsProvider> historicalStatsProviders,
            List<MatchStatisticsProvider> matchStatisticsProviders) {
        this.leagueProviders = index(leagueProviders, LeagueProvider::getProvider, "LeagueProvider");
        this.seasonProviders = index(seasonProviders, SeasonProvider::getProvider, "SeasonProvider");
        this.teamProviders = index(teamProviders, TeamProvider::getProvider, "TeamProvider");
        this.fixtureProviders = index(fixtureProviders, FixtureProvider::getProvider, "FixtureProvider");
        this.standingsProviders = index(standingsProviders, StandingsProvider::getProvider, "StandingsProvider");
        this.oddsProviders = index(oddsProviders, OddsProvider::getProvider, "OddsProvider");
        this.historicalStatsProviders = index(
                historicalStatsProviders, HistoricalStatsProvider::getProvider, "HistoricalStatsProvider");
        this.matchStatisticsProviders = index(
                matchStatisticsProviders, MatchStatisticsProvider::getProvider, "MatchStatisticsProvider");
    }

    /** Backward-compatible constructor for integrations predating match-statistics SPI. */
    public ProviderRegistry(
            List<LeagueProvider> leagueProviders,
            List<SeasonProvider> seasonProviders,
            List<TeamProvider> teamProviders,
            List<FixtureProvider> fixtureProviders,
            List<StandingsProvider> standingsProviders,
            List<OddsProvider> oddsProviders,
            List<HistoricalStatsProvider> historicalStatsProviders) {
        this(leagueProviders, seasonProviders, teamProviders, fixtureProviders,
                standingsProviders, oddsProviders, historicalStatsProviders, List.of());
    }

    /** Backward-compatible constructor for integrations predating SeasonProvider. */
    public ProviderRegistry(
            List<LeagueProvider> leagueProviders,
            List<TeamProvider> teamProviders,
            List<FixtureProvider> fixtureProviders,
            List<StandingsProvider> standingsProviders,
            List<OddsProvider> oddsProviders,
            List<HistoricalStatsProvider> historicalStatsProviders) {
        this(leagueProviders, List.of(), teamProviders, fixtureProviders,
                standingsProviders, oddsProviders, historicalStatsProviders, List.of());
    }

    public LeagueProvider league(Provider provider) {
        return get(leagueProviders, provider, "LeagueProvider");
    }

    public boolean hasSeasonProvider(Provider provider) {
        return provider != null && seasonProviders.containsKey(provider);
    }

    public SeasonProvider season(Provider provider) {
        return get(seasonProviders, provider, "SeasonProvider");
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

    public HistoricalStatsProvider historicalStats(Provider provider) {
        return get(historicalStatsProviders, provider, "HistoricalStatsProvider");
    }

    public MatchStatisticsProvider matchStatistics(Provider provider) {
        return get(matchStatisticsProviders, provider, "MatchStatisticsProvider");
    }

    public boolean hasMatchStatisticsProvider(Provider provider) {
        return provider != null && matchStatisticsProviders.containsKey(provider);
    }

    private static <T> Map<Provider, T> index(
            List<T> providers,
            Function<T, Provider> providerCode,
            String resourceName) {
        Map<Provider, T> indexed = new EnumMap<>(Provider.class);
        for (T strategy : providers) {
            if (strategy == null) continue;
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
            throw new IllegalStateException("No " + resourceName + " configured for provider " + provider);
        }
        return strategy;
    }
}
