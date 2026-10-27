# Football job logic refactor

## Goals

- Keep the existing persisted `FBCronjobInitial` registry and Quartz scheduler.
- Make job completion state reliable enough for the workflow orchestrator.
- Bound database reads and avoid per-team-pair queries for historical H2H metrics.
- Keep prediction processing bounded and resumable across scheduled runs.
- Avoid loading every active subscriber into memory.
- Move publication queue limits out of the worker implementation and into typed application settings.

## Changes

### Shared Quartz execution

`AbstractJob` now records failures even when loading the persisted job definition fails, logs elapsed time consistently, and uses a volatile interrupt flag. Jobs that iterate through batches can check `shouldStop()` and stop cooperatively.

Manual trigger handling persists `RUNNING` before invoking Quartz. Previously, a fast job could finish and the listener could save `COMPLETE`, after which the API thread could overwrite it with a late `RUNNING` save. Manual triggers for paused jobs now fail explicitly.

The orchestrator waits for the persisted state to transition to `COMPLETE`, instead of relying on timestamp precision to decide whether a new execution was recorded. The existing child-job order and persisted cron registry remain in place.

### Prediction processing

- Constructor injection replaces field injection.
- The prediction time window is explicit: 6 to 54 hours before kickoff.
- Candidate matches are loaded in deterministic pages of 100, with the associations needed by the engines fetched in the same query.
- Existing predictions are loaded per batch instead of querying once per match.
- Match-level failures are counted and logged with stack traces; the job reports candidates, processed, skipped and failed counts.
- The job checks for Quartz interruption between batches and matches.

### Advanced metrics

- Team form now loads finished matches once per current season and derives each team's latest ten results in memory, rather than querying once for every team/season pair.
- H2H metrics are aggregated from finished, scored matches in stable pages. The previous implementation queried every ordered pair of teams, including pairs that had never played each other.
- Existing H2H records are fetched with their team associations in one query and updated in bulk. Directional records are retained: A-vs-B and B-vs-A each represent the selected team's perspective.
- Metric calculations are rerunnable and update timestamps only when a calculation is persisted.

### Subscription expiry and publication queue

- Subscription expiry uses keyset pagination by subscriber ID. This avoids offset-pagination skips when expiring a subscriber removes it from the ACTIVE result set.
- Publication batch size, maximum attempts and retry delay are now configurable through `app.publication.worker` and `AppProperties`.

## Current job responsibilities

| Job | Responsibility |
| --- | --- |
| `FootballWorkflowOrchestrationJob` | Run the persisted end-to-end stages in dependency order, then generate content drafts |
| `FootballDataBootstrapJob` | Bootstrap configured core football data |
| `LeagueSyncJob`, `TeamSyncJob` | Refresh metadata through the configured/dynamic provider routing |
| `MatchFixtureSyncJob`, `MatchResultSyncJob` | Refresh upcoming fixtures and recent results |
| `MatchStatisticsSyncJob` | Enrich finished matches using the existing SofaScore statistics pipeline |
| `AdvancedMetricsJob` | Refresh current-season form and historical H2H aggregates |
| `MatchProcessPredictionJob` | Calculate predictions for eligible upcoming matches |
| `PredictionEvaluationJob` | Evaluate newly completed predictions |
| `WalkForwardBackTestJob` | Run the expensive historical model evaluation separately from the content path |
| `PublicationWorkerQuartzJob` | Process approved, due Telegram publications through the existing worker |
| `SubscriptionExpiryCheckJob` | Expire subscriptions and count those approaching expiry |

## Configuration

Defaults are conservative and preserve existing behavior:

- `app.publication.worker.batch-size: 10`
- `app.publication.worker.max-attempts: 5`
- `app.publication.worker.retry-delay-millis: 300000`

Environment overrides:

- `FB_PUBLICATION_BATCH_SIZE`
- `FB_PUBLICATION_MAX_ATTEMPTS`
- `FB_PUBLICATION_RETRY_DELAY_MILLIS`

## Validation still required

The repository CI should run `mvn -B -ntp clean verify`. A real deployment should additionally verify the Quartz JDBC state transitions and run a controlled dry-run of the Telegram publication queue. No live social posts should be used as a test.

This refactor does not claim that upstream provider data is complete or that the full pipeline has been validated against a production database; it makes job execution more bounded, observable and consistent.
