# Durable football workflow orchestration

The daily workflow is coordinated in this order:

1. `DATA_INGESTION` — invoke the existing full rebuild use case.
2. `DATA_READINESS` — audit every league-season before downstream analytics.
3. `ADVANCED_METRICS` — invoke the existing advanced-metrics processor.
4. `PREDICTIONS` — invoke the existing upcoming-match prediction processor.
5. `PRE_MATCH_CONTENT` — invoke the existing content lifecycle.

## Persistence and recovery

- `fb_workflow_runs` stores one workflow execution per idempotency key.
- `fb_workflow_stage_executions` stores one checkpoint per run and stage.
- Each stage start/completion/failure is committed in its own `REQUIRES_NEW` transaction.
- Completed stages are skipped on resume; failed and interrupted stages are retried.
- A six-hour execution lease and pessimistic run-row lock prevent two scheduler nodes from processing the same workflow simultaneously. A per-claim execution token fences off a stale worker after another coordinator takes over an expired lease. An expired lease allows a later invocation to recover a run left active by process termination.
- The UTC workflow date is used as the idempotency key, so retries of the same daily workflow reuse the same durable run.

The ingestion and analytics implementations remain the owners of their own persistence and processing semantics. A workflow checkpoint records stage completion; it does not create a distributed transaction with those downstream services.

## Readiness policy

The gate uses the existing persisted data coverage audit and validates each league-season for:

- Fixture presence.
- Finished-fixture score coverage.
- Finished-fixture xG coverage.
- Persisted team-statistics coverage.
- Statistics snapshot presence and, for current seasons, snapshot freshness.

Defaults can be overridden with Spring properties:

- `mmo.workflow.readiness.minimum-score-coverage` (default `0.95`)
- `mmo.workflow.readiness.minimum-xg-coverage` (default `0.80`)
- `mmo.workflow.readiness.minimum-team-statistics-coverage` (default `0.80`)
- `mmo.workflow.readiness.maximum-snapshot-age-hours` (default `72`)

A failed readiness gate prevents metrics, predictions, and content from running. Thresholds should be calibrated against the expected historical-season coverage before enabling the coordinator in production.

## Operational note

A stage can perform external or multi-transaction work before its completion checkpoint is written. If the process stops in that interval, the stage is executed again after lease recovery. Therefore each invoked use case must remain idempotent; the workflow layer does not claim atomicity across provider APIs, analytics writes, and content generation.
