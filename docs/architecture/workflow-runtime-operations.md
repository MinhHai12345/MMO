# Football workflow runtime operations

## Quartz integration

`FootballWorkflowOrchestrationQuartzJob` is the Quartz adapter for the durable
`FootballWorkflowCoordinator`. It delegates stage ordering, checkpoint persistence,
lease ownership and recovery to the application service. The adapter does not contain
business or analytics logic.

The adapter uses the UTC calendar date as the workflow date and forwards Quartz's
interrupt signal to the coordinator. A workflow result with status `FAILED` is surfaced
as a Quartz job error; `INTERRUPTED` is treated as an intentional stop.

## Initial rollout policy

The initializer registers the job in the `FBJobGroup` registry using the cron expression
`0 0 0 1 1 ? 2099`. This keeps the Quartz job definition available for operator-triggered
execution without enabling a recurring schedule.

This is intentional: the coordinator's `DATA_INGESTION` stage invokes the incremental
`ActiveSeasonDataSyncUseCase`, not the historical full-rebuild pipeline. Historical
backfill remains owned by `CoreDataBackfillJob`, while match enrichment remains owned by
`MatchEnrichmentJob`; the workflow does not duplicate those schedules.

Operators can trigger the registered CronJob through the existing CronJob management
surface after verifying the deployment's access controls. Keep the workflow manual-only
until an end-to-end MySQL/Quartz test validates stage recovery, provider-lock contention,
and the complete data-readiness-to-content path.

## Interrupt and recovery behavior

- Quartz `interrupt()` is observed through `AbstractJob.shouldStop()`.
- The coordinator persists completed stage checkpoints independently.
- On interruption, the coordinator releases its execution lease and resets only
  in-progress stage state; completed stages remain reusable.
- If the process terminates before it can release the lease, a later run can recover
  after lease expiry. A new execution token fences the previous worker from writing
  subsequent checkpoints.
- A stage may be re-executed if its side effects committed but its completion checkpoint
  did not. Underlying ingestion, analytics and content use cases must remain idempotent.

## Suggested next production gate

Before switching the job to a recurring cron, add an end-to-end test against MySQL and
real Quartz persistence. Verify interruption, lease expiry, resume, and that a skipped
active-season synchronization cannot be checkpointed as successful.
