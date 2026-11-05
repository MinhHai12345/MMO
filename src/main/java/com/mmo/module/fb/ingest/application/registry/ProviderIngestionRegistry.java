package com.mmo.module.fb.ingest.application.registry;

import com.mmo.module.fb.ingest.application.port.out.provider.*;
import com.mmo.module.fb.ingest.enums.Provider;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/** Application-owned registry of provider capabilities, indexed by provider identity. */
@Component
public class ProviderIngestionRegistry {
    private final Map<Provider, LeagueIngestionPort> leagues;
    private final Map<Provider, SeasonIngestionPort> seasons;
    private final Map<Provider, TeamIngestionPort> teams;
    private final Map<Provider, FixtureIngestionPort> fixtures;
    private final Map<Provider, StandingsIngestionPort> standings;
    private final Map<Provider, OddsIngestionPort> odds;
    private final Map<Provider, HistoricalStatsIngestionProviderPort> historicalStats;
    private final Map<Provider, MatchStatisticsIngestionProviderPort> matchStatistics;
    private final Map<Provider, PlayerLineupsIngestionPort> lineups;

    public ProviderIngestionRegistry(
            List<? extends LeagueIngestionPort> leagues,
            List<? extends SeasonIngestionPort> seasons,
            List<? extends TeamIngestionPort> teams,
            List<? extends FixtureIngestionPort> fixtures,
            List<? extends StandingsIngestionPort> standings,
            List<? extends OddsIngestionPort> odds,
            List<? extends HistoricalStatsIngestionProviderPort> historicalStats,
            List<? extends MatchStatisticsIngestionProviderPort> matchStatistics,
            List<? extends PlayerLineupsIngestionPort> lineups) {
        this.leagues = index(leagues, LeagueIngestionPort::getProvider, "league");
        this.seasons = index(seasons, SeasonIngestionPort::getProvider, "season");
        this.teams = index(teams, TeamIngestionPort::getProvider, "team");
        this.fixtures = index(fixtures, FixtureIngestionPort::getProvider, "fixture");
        this.standings = index(standings, StandingsIngestionPort::getProvider, "standings");
        this.odds = index(odds, OddsIngestionPort::getProvider, "odds");
        this.historicalStats = index(historicalStats, HistoricalStatsIngestionProviderPort::getProvider, "historical statistics");
        this.matchStatistics = index(matchStatistics, MatchStatisticsIngestionProviderPort::getProvider, "match statistics");
        this.lineups = index(lineups, PlayerLineupsIngestionPort::getProvider, "lineups");
    }

    public LeagueIngestionPort league(Provider provider) { return require(leagues, provider, "league"); }
    public SeasonIngestionPort season(Provider provider) { return require(seasons, provider, "season"); }
    public TeamIngestionPort team(Provider provider) { return require(teams, provider, "team"); }
    public FixtureIngestionPort fixture(Provider provider) { return require(fixtures, provider, "fixture"); }
    public StandingsIngestionPort standings(Provider provider) { return require(standings, provider, "standings"); }
    public OddsIngestionPort odds(Provider provider) { return require(odds, provider, "odds"); }
    public HistoricalStatsIngestionProviderPort historicalStats(Provider provider) {
        return require(historicalStats, provider, "historical statistics");
    }
    public MatchStatisticsIngestionProviderPort matchStatistics(Provider provider) {
        return require(matchStatistics, provider, "match statistics");
    }
    public PlayerLineupsIngestionPort playerLineups(Provider provider) {
        return require(lineups, provider, "lineups");
    }
    public boolean hasSeasonProvider(Provider provider) { return provider != null && seasons.containsKey(provider); }
    public boolean hasMatchStatisticsProvider(Provider provider) {
        return provider != null && matchStatistics.containsKey(provider);
    }
    public boolean hasPlayerLineupsProvider(Provider provider) {
        return provider != null && lineups.containsKey(provider);
    }

    private static <T> Map<Provider, T> index(
            List<? extends T> providers, Function<T, Provider> identity, String capability) {
        Map<Provider, T> result = new EnumMap<>(Provider.class);
        if (providers != null) {
            for (T provider : providers) {
                if (provider == null) continue;
                Provider key = identity.apply(provider);
                if (key == null) throw new IllegalStateException(capability + " provider returned null identity");
                if (result.putIfAbsent(key, provider) != null) {
                    throw new IllegalStateException("Duplicate " + capability + " provider: " + key);
                }
            }
        }
        return Map.copyOf(result);
    }

    private static <T> T require(Map<Provider, T> providers, Provider provider, String capability) {
        if (provider == null) throw new IllegalArgumentException("Selected provider must not be null for " + capability);
        T result = providers.get(provider);
        if (result == null) throw new IllegalStateException("No " + capability + " provider configured for " + provider);
        return result;
    }
}
