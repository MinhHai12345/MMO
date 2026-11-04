# FB Job Inventory and Target Architecture

## Runtime jobs

| Job | Responsibility | Execution |
|---|---|---|
| FootballDataBootstrapJob | Initial core bootstrap and historical backfill | Monthly recovery schedule and manual trigger |
| FootballDataSyncJob | Incremental leagues, teams, recent results, upcoming fixtures, standings, odds and Football-Data.co.uk import | Every 6 hours |
| MatchEnrichmentJob | Historical match metrics and player statistics | Every 12 hours |
| AdvancedMetricsJob | Team form and head-to-head aggregates | Every 6 hours |
| MatchProcessPredictionJob | Upcoming-match predictions | Every 3 hours |
| PredictionEvaluationJob | Evaluate predictions against completed results | Every 30 minutes |
| WalkForwardBackTestJob | Historical model evaluation | Weekly / manual |
| FootballWorkflowOrchestrationJob | Generate PRE_MATCH content drafts only | Daily |
| PublicationWorkerQuartzJob | Publish approved, due content | Every minute |
| SubscriptionExpiryCheckJob | Subscription expiry processing | Daily |

## Removed jobs

The following jobs duplicated the consolidated ingestion workflow and are deleted from source and the persisted Quartz registry:

- LeagueSyncJob
- TeamSyncJob
- MatchFixtureSyncJob
- MatchResultSyncJob
- MatchStatisticsSyncJob
- FootballDataUkCoreSyncJob
- UnderStatHistoricalStatsJob

Obsolete persisted rows without active source implementations are also deleted during initialization: UnderStatCatalogSyncJob, MatchStatusTrackerJob, CrawlerHealthCheckJob, DataCleanupJob and MatchLineupOddsUpdateJob.

## Execution boundaries

Quartz jobs are thin adapters. They read execution parameters and invoke one application use case. They do not call provider adapters, provider clients, persistence repositories, or provider-specific implementations.

The scheduled content workflow does not trigger independently scheduled ingestion, enrichment, analytics, or prediction jobs. This prevents duplicate execution and makes each job independently observable and manually triggerable.

## Ingestion flow

`FootballDataSyncJob -> FootballDataSyncService -> Resource Ingestion Services -> ProviderRegistry -> Provider Adapter -> Provider DTO Mapper -> Provider-neutral Model -> Persistence Service`

`MatchEnrichmentJob -> MatchEnrichmentService -> Historical/Player Statistics Ingestion -> Provider Adapter -> Provider Mapper -> Canonical Match Persistence`

Bootstrap remains separate from incremental synchronization so historical backfills do not run on every poll.

## Operational invariants

- Provider identity is selected per resource through configuration or one-execution overrides.
- Provider adapters return source-neutral models; they do not persist MMO entities.
- Persistence and canonical matching belong to ingestion application/persistence services.
- One provider's transport failure must not silently mark its resource as successfully synchronized.
- Legacy jobs are removed from both Quartz and `cron_jobs`, not merely paused.
- Job execution remains idempotent and uses bounded date windows for incremental fixture/result sync.
