package com.mmo.module.fb.ingest.application.orchestration;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.application.exception.IngestionPartialException;
import com.mmo.module.fb.ingest.application.exception.ProviderTransportException;
import com.mmo.module.fb.ingest.application.lock.IngestionDistributedLock;
import com.mmo.module.fb.ingest.application.model.ProviderOverrides;
import com.mmo.module.fb.ingest.application.port.in.FixtureIngestionService;
import com.mmo.module.fb.ingest.application.port.in.FootballDataBootstrapService;
import com.mmo.module.fb.ingest.application.port.in.LeagueIngestionService;
import com.mmo.module.fb.ingest.application.port.in.SeasonIngestionService;
import com.mmo.module.fb.ingest.application.port.in.StandingsIngestionService;
import com.mmo.module.fb.ingest.application.port.in.TeamIngestionService;
import com.mmo.module.fb.ingest.application.port.out.ProviderCheckpointPort;
import com.mmo.module.fb.ingest.application.port.out.provider.SeasonBatchIngestionPort;
import com.mmo.module.fb.ingest.application.registry.SeasonBatchIngestionRegistry;
import com.mmo.module.fb.ingest.enums.CheckpointStatus;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.Dataset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Coordinates the one-time historical Core backfill and recovery workflow.
 *
 * <p>DataSourcePolicy selects the provider owner and allowed fallbacks independently for
 * each dataset/season. Providers that expose the optional season-batch capability may prepare
 * and import their own historical season data without adding provider-specific branches here.
 * Completion is persisted per resource/provider.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoreBackfillOrchestrator implements FootballDataBootstrapService {

    private static final String INITIAL_CORE_CHECKPOINT = "CORE_INITIAL_BACKFILL";

    private final IngestionDistributedLock distributedLock;
    private final AppProperties appProperties;
    private final ProviderCheckpointPort checkpointStore;
    private final IngestionPlanFactory planFactory;
    private final SeasonBatchIngestionRegistry seasonBatchRegistry;
    private final LeagueIngestionService leagueIngestionService;
    private final SeasonIngestionService seasonIngestionService;
    private final TeamIngestionService teamIngestionService;
    private final FixtureIngestionService fixtureIngestionService;
    private final StandingsIngestionService standingsIngestionService;

    @Override
    public void syncCoreData() {
        syncCoreData(ProviderOverrides.defaults());
    }

    @Override
    public void syncCoreData(ProviderOverrides overrides) {
        boolean executed = distributedLock.execute(IngestionDistributedLock.GLOBAL_INGEST_LOCK,
                () -> syncCoreDataLocked(overrides));
        if (!executed) {
            log.warn("Bootstrap skipped because another ingestion workflow is running");
        }
    }

    private void syncCoreDataLocked(ProviderOverrides overrides) {
        List<String> failedStages = new ArrayList<>();
        // Bootstrap catalogue providers come from the configured resource routes.
        // Season-batch providers prepare their own season catalogue from their source format.
        Provider leagueCatalogueProvider = appProperties.getIngestion().getProviders().getLeague();
        Provider seasonCatalogueProvider = appProperties.getIngestion().getProviders().getSeason();
        if (seasonCatalogueProvider == null) seasonCatalogueProvider = leagueCatalogueProvider;
        validateProvider(leagueCatalogueProvider);
        validateProvider(seasonCatalogueProvider);
        if (!seasonBatchRegistry.supports(leagueCatalogueProvider, Dataset.LEAGUE)) {
            runStageCollecting("league-catalogue/" + leagueCatalogueProvider,
                    () -> leagueIngestionService.syncLeaguesForProvider(leagueCatalogueProvider), failedStages);
        }
        if (!seasonBatchRegistry.supports(seasonCatalogueProvider, Dataset.SEASON)) {
            runStageCollecting("season-catalogue/" + seasonCatalogueProvider,
                    () -> seasonIngestionService.syncSeasonsForProvider(seasonCatalogueProvider), failedStages);
        }

        if (overrides != null && (overrides.team() != null || overrides.fixture() != null
                || overrides.standings() != null)) {
            log.info("Ignoring resource-level provider overrides for historical Core; "
                    + "DataSourcePolicy is authoritative per dataset and season");
        }

        List<SeasonBatchIngestionPort> batchProviders = seasonBatchRegistry.all().stream()
                .filter(provider -> isProviderEnabled(provider.getProvider()))
                .toList();
        boolean batchCoreRequired = batchProviders.stream().anyMatch(provider ->
                seasonBatchRegistry.supports(provider.getProvider(), Dataset.SEASON)
                        || seasonBatchRegistry.supports(provider.getProvider(), Dataset.TEAM)
                        || seasonBatchRegistry.supports(provider.getProvider(), Dataset.FIXTURE));
        for (SeasonBatchIngestionPort provider : batchProviders) {
            if (!seasonBatchRegistry.supports(provider.getProvider(), Dataset.SEASON)) continue;
            try {
                runStage(provider.getProvider() + "/prepare-historical-seasons",
                        provider::prepareHistoricalSeasons);
            } catch (RuntimeException exception) {
                failedStages.add(provider.getProvider() + "/prepare-seasons: " + exception.getMessage());
                log.error("Unable to prepare historical seasons for provider {}", provider.getProvider(), exception);
            }
        }

        IngestionPlan plan = planFactory.historicalPlan();
        List<IngestionWorkUnit> catalogueSeasonUnits = plan.units(
                Dataset.SEASON, seasonCatalogueProvider, IngestionWorkUnit.Role.PRIMARY);
        String seasonCatalogueFailurePrefix = seasonCatalogueProvider + "/prepare-seasons";
        if (failedStages.stream().noneMatch(error -> error.startsWith("season-catalogue/")
                || error.startsWith(seasonCatalogueFailurePrefix))) {
            catalogueSeasonUnits.forEach(unit -> checkpointStore.markStatus(unit.provider(), Dataset.SEASON,
                    unit.leagueId(), unit.season(), CheckpointStatus.COMPLETED));
        }
        List<IngestionWorkUnit> nonBatchTeams = plan.units().stream()
                .filter(unit -> unit.dataset() == Dataset.TEAM)
                .filter(unit -> !seasonBatchRegistry.supports(unit.provider(), Dataset.TEAM))
                .toList();
        List<IngestionWorkUnit> nonBatchFixtures = plan.units().stream()
                .filter(unit -> unit.dataset() == Dataset.FIXTURE)
                .filter(unit -> !seasonBatchRegistry.supports(unit.provider(), Dataset.FIXTURE))
                .toList();
        log.info("Historical Core plan created: units={}, nonBatchTeams={}, nonBatchFixtures={}, batchProviders={}",
                plan.units().size(), nonBatchTeams.size(), nonBatchFixtures.size(),
                batchProviders.stream().map(SeasonBatchIngestionPort::getProvider).toList());

        if (plan.units().isEmpty() && !batchCoreRequired) {
            if (!failedStages.isEmpty()) {
                throw new IllegalStateException("Historical Core catalogue initialization incomplete: "
                        + failedStages);
            }
            log.info("Historical Core plan contains no supported season work; nothing to backfill");
            return;
        }

        List<IngestionUnitResult> unitResults = new ArrayList<>();
        nonBatchTeams.forEach(unit -> runUnit(unit, () ->
                teamIngestionService.syncSeasonForProvider(unit.provider(), unit.seasonId()), failedStages, unitResults));
        nonBatchFixtures.forEach(unit -> runUnit(unit, () ->
                fixtureIngestionService.syncSeasonForProvider(unit.provider(), unit.seasonId(), null, null),
                failedStages, unitResults));

        for (SeasonBatchIngestionPort provider : batchProviders) {
            Provider providerId = provider.getProvider();
            List<Long> batchSeasonIds = plan.units().stream()
                    .filter(unit -> unit.provider() == providerId
                            && (unit.dataset() == Dataset.SEASON || unit.dataset() == Dataset.TEAM
                            || unit.dataset() == Dataset.FIXTURE)
                            && seasonBatchRegistry.supports(providerId, unit.dataset()))
                    .map(IngestionWorkUnit::seasonId).distinct().sorted().toList();
            for (Long seasonId : batchSeasonIds) {
                try {
                    runStage(providerId + "/core/season=" + seasonId,
                            () -> provider.syncCoreSeason(seasonId, true));
                } catch (RuntimeException exception) {
                    failedStages.add(providerId + "/core/season=" + seasonId
                            + ": " + exception.getMessage());
                    log.warn("Season-batch provider {} failed season {}; continuing with remaining seasons",
                            providerId, seasonId, exception);
                }
            }
            if (!batchSeasonIds.isEmpty()
                    && failedStages.stream().noneMatch(error -> error.startsWith(providerId + "/"))) {
                checkpointStore.markCompleted(providerId, INITIAL_CORE_CHECKPOINT);
            }
        }

        IngestionExecutionReport report = new IngestionExecutionReport(unitResults);
        log.info("Historical Core work-unit report: {}", report.summary());
        if (!failedStages.isEmpty()) {
            throw new IllegalStateException("Historical Core backfill incomplete: " + failedStages
                    + "; " + report.summary());
        }
        log.info("Historical Core backfill orchestration completed: {}", report.summary());
    }

    private void runUnit(IngestionWorkUnit unit, Runnable operation, List<String> failedStages,
                         List<IngestionUnitResult> results) {
        String name = unit.provider() + "/" + unit.dataset() + "/league=" + unit.leagueId()
                + "/season=" + unit.season();
        IngestionUnitResult result = new IngestionUnitExecutor(checkpointStore)
                .execute(unit, true, () -> runStage(name, operation));
        results.add(result);
        if (!result.isSuccessful()) {
            failedStages.add(name + " [" + result.status() + "]: " + result.message());
            log.warn("Independent historical work unit finished with {}: {}", result.status(), name);
        }
    }

    /**
     * Explicit multi-provider historical import. Each provider is mapped to the same
     * canonical Core entities before teams and fixtures are imported.
     */
    @Override
    public void syncCoreDataForProviders(List<Provider> providers) {
        boolean executed = distributedLock.execute(IngestionDistributedLock.GLOBAL_INGEST_LOCK,
                () -> syncCoreDataForProvidersLocked(providers));
        if (!executed) {
            log.warn("Multi-provider bootstrap skipped because another ingestion workflow is running");
        }
    }

    private void syncCoreDataForProvidersLocked(List<Provider> providers) {
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("At least one ingestion provider is required");
        }
        if (providers.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Provider list must not contain null");
        }

        List<Provider> ordered = new ArrayList<>(new LinkedHashSet<>(providers));
        ordered.sort(java.util.Comparator.comparing(Provider::name));
        ordered.forEach(this::validateProvider);

        log.info("Starting multi-provider historical Core import: {}", ordered);
        for (Provider provider : ordered) {
            if (!seasonBatchRegistry.supports(provider, Dataset.LEAGUE)) {
                runStage("multi-provider/leagues/" + provider,
                        () -> leagueIngestionService.syncLeaguesForProvider(provider));
            }
        }
        for (Provider provider : ordered) {
            if (seasonBatchRegistry.supports(provider, Dataset.SEASON)) {
                runStage("multi-provider/prepare-seasons/" + provider,
                        () -> seasonBatchRegistry.require(provider).prepareHistoricalSeasons());
            } else {
                runStage("multi-provider/seasons/" + provider,
                        () -> seasonIngestionService.syncSeasonsForProvider(provider));
            }
        }
        IngestionPlan plan = planFactory.historicalPlan();
        List<String> failedStages = new ArrayList<>();
        List<IngestionUnitResult> results = new ArrayList<>();
        for (Provider provider : ordered) {
            List<Long> batchSeasonIds = plan.units().stream()
                    .filter(unit -> unit.provider() == provider
                            && (unit.dataset() == Dataset.SEASON || unit.dataset() == Dataset.TEAM
                            || unit.dataset() == Dataset.FIXTURE)
                            && seasonBatchRegistry.supports(provider, unit.dataset()))
                    .map(IngestionWorkUnit::seasonId).distinct().sorted().toList();
            if (!batchSeasonIds.isEmpty()) {
                SeasonBatchIngestionPort batchProvider = seasonBatchRegistry.require(provider);
                for (Long seasonId : batchSeasonIds) {
                    try {
                        runStage("multi-provider/core/" + provider + "/season=" + seasonId,
                                () -> batchProvider.syncCoreSeason(seasonId, true));
                    } catch (RuntimeException exception) {
                        failedStages.add("multi-provider/core/" + provider + "/season=" + seasonId
                                + ": " + exception.getMessage());
                    }
                }
            }
            plan.units().stream().filter(unit -> unit.provider() == provider
                            && unit.dataset() == Dataset.TEAM)
                    .filter(unit -> !seasonBatchRegistry.supports(provider, Dataset.TEAM))
                    .forEach(unit -> runUnit(unit, () ->
                            teamIngestionService.syncSeasonForProvider(provider, unit.seasonId()),
                            failedStages, results));
            plan.units().stream().filter(unit -> unit.provider() == provider
                            && unit.dataset() == Dataset.FIXTURE)
                    .filter(unit -> !seasonBatchRegistry.supports(provider, Dataset.FIXTURE))
                    .forEach(unit -> runUnit(unit, () ->
                            fixtureIngestionService.syncSeasonForProvider(provider, unit.seasonId(), null, null),
                            failedStages, results));
            plan.units().stream().filter(unit -> unit.provider() == provider
                            && unit.dataset() == Dataset.STANDINGS)
                    .filter(unit -> !seasonBatchRegistry.supports(provider, Dataset.STANDINGS))
                    .forEach(unit -> runUnit(unit, () ->
                            standingsIngestionService.syncSeasonForProvider(provider, unit.seasonId()),
                            failedStages, results));
        }
        IngestionExecutionReport report = new IngestionExecutionReport(results);
        log.info("Multi-provider historical Core execution report: providers={}, {}", ordered, report.summary());
        if (!failedStages.isEmpty()) {
            throw new IllegalStateException("Multi-provider historical Core import incomplete: "
                    + failedStages + "; " + report.summary());
        }
        log.info("Multi-provider historical Core import completed: {}", ordered);
    }

    private boolean isProviderEnabled(Provider provider) {
        AppProperties.ProviderConfig config = appProperties.getIngestion().getProviderConfig().get(provider);
        return config != null && config.isEnabled();
    }

    private void runStageCollecting(String name, Runnable operation, List<String> failedStages) {
        try {
            runStage(name, operation);
        } catch (RuntimeException exception) {
            failedStages.add(name + ": " + exception.getMessage());
            log.warn("Catalogue stage failed; continuing independent backfill units: {}", name, exception);
        }
    }

    private void runStage(String name, Runnable operation) {
        long startedAt = System.nanoTime();
        log.info("Core ingestion stage STARTED: {}", name);
        try {
            operation.run();
            log.info("Core ingestion stage COMPLETED: {} durationMs={}",
                    name, (System.nanoTime() - startedAt) / 1_000_000);
        } catch (ProviderTransportException exception) {
            log.error("Core ingestion stage FAILED: {} durationMs={}",
                    name, (System.nanoTime() - startedAt) / 1_000_000, exception);
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Core ingestion stage FAILED: {} durationMs={} errorType={} message={}",
                    name, (System.nanoTime() - startedAt) / 1_000_000,
                    exception.getClass().getSimpleName(), exception.getMessage(), exception);
            throw exception;
        }
    }

    private void validateProvider(Provider provider) {
        if (provider == null) throw new IllegalStateException("Ingestion provider is not configured");
        AppProperties.ProviderConfig config = appProperties.getIngestion().getProviderConfig().get(provider);
        if (config == null || !config.isEnabled()) {
            throw new IllegalStateException(provider + " is disabled or not configured for bootstrap");
        }
        String requiredTokenVariable = switch (provider) {
            case API_FOOTBALL -> "API_FOOTBALL_API_KEY";
            case FOOTBALL_DATA -> "FOOTBALL_DATA_API_KEY";
            default -> null;
        };
        if (requiredTokenVariable != null) {
            String token = config.getApiKey();
            if (!StringUtils.hasText(token) || "mock".equalsIgnoreCase(token.trim())) {
                throw new IllegalStateException("Provider API token is not configured. Set "
                        + requiredTokenVariable + ".");
            }
        }
    }
}
