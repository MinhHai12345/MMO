# FB Job Inventory and Target Architecture

## Scope
Phase 1 and Phase 2 consolidate Quartz entry points for inventory and ingestion. Existing ingestion services, provider adapters, mappers and persistence services remain. No business service or legacy job class is deleted.

## Existing inventory on main before this change

| Existing Quartz job | Responsibility | Decision |
|---|---|---|
| FootballWorkflowOrchestrationJob | Triggers bootstrap, individual sync jobs, analytics and content | Keep; point at consolidated ingestion jobs |
| FootballDataBootstrapJob | Initial core bootstrap/backfill | Keep; run on demand/when stale |
| LeagueSyncJob | League/season refresh | Pause registry row; retain class/service |
| TeamSyncJob | Team refresh | Pause registry row; retain class/service |
| MatchFixtureSyncJob | Upcoming fixture refresh | Pause registry row; retain class/service |
| MatchResultSyncJob | Recent result refresh | Pause registry row; retain class/service |
| MatchStatisticsSyncJob | SofaScore bootstrap, match and player statistics | Pause registry row; retain class/service |
| FootballDataUkCoreSyncJob | Football-Data.co.uk all mapped seasons | Pause registry row; invoke through FootballDataSyncJob |
| UnderStatHistoricalStatsJob | Understat and Football-Data.co.uk | Pause registry row; responsibility split |
| AdvancedMetricsJob | Team form and H2H | Retain until Analytics phase |
| MatchProcessPredictionJob | Upcoming match predictions | Retain until Analytics phase |
| PredictionEvaluationJob | Evaluate predictions after results | Retain |
| WalkForwardBackTestJob | Historical model evaluation | Retain as weekly/offline job |
| PublicationWorkerQuartzJob | Publish due approved content | Retain |
| SubscriptionExpiryCheckJob | Expire subscriptions | Retain conditionally; outside this phase |

Legacy rows already handled by initializer: UnderStatCatalogSyncJob, MatchStatusTrackerJob, CrawlerHealthCheckJob, DataCleanupJob and MatchLineupOddsUpdateJob.

## Consolidated ingestion jobs

### FootballDataBootstrapJob
Initial/backfill operation. Resolves configured providers by resource. It is not the frequent incremental sync.

### FootballDataSyncJob
Runs canonical Core sync in dependency order:
1. League and season metadata.
2. Teams.
3. Recent results (UTC today minus 7 days through today).
4. Upcoming fixtures (UTC today through today plus 14 days).
5. Canonical standings (skipped for providers without standings).
6. Football-Data.co.uk import, enriching canonical matches with CSV results, statistics and odds.

Provider-specific services remain independent and are selected through existing provider routing and one-execution provider overrides.

### MatchEnrichmentJob
Uses the configured historical-stats provider (Understat or SofaScore), then syncs player match statistics. It does not create canonical teams or fixtures. Football-Data.co.uk is not called from this job.

## Target flow
FootballWorkflowOrchestrationJob -> Bootstrap when required -> FootballDataSyncJob -> MatchEnrichmentJob -> AdvancedMetricsJob -> MatchProcessPredictionJob -> Content generation -> PublicationWorkerQuartzJob.

PredictionEvaluationJob remains independent. WalkForwardBackTestJob remains weekly/manual.

## Registry migration policy
- Register the consolidated jobs with stable names and schedules.
- Pause old job rows, not delete them. Pausing updates both Quartz and the database state.
- Retain old job classes and ingestion services for compatibility and rollback.
- Never automatically resume legacy rows.
- Existing operator-paused jobs stay paused when their schedule metadata is updated.
- Registry updates must not silently reactivate an operator-paused job.

## Operational invariants
- Provider is a data source, not a Quartz job boundary.
- Bootstrap/backfill is distinct from incremental sync.
- Understat and Football-Data.co.uk have separate responsibilities.
- Enrichment operates on canonical MMO fixtures.
- Provider persistence remains idempotent.
- Job logs include stage and duration; failures propagate to Quartz.
