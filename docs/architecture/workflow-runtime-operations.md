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

This is intentional: the current coordinator's `DATA_INGESTION` stage invokes the full
data rebuild use case. Automatically scheduling that pipeline before measuring provider
load, database impact and total runtime could duplicate the existing ingestion schedules
and create avoidable load.

Operators can trigger the registered CronJob through the existing CronJob management
surface after verifying the deployment's access controls. Do not enable a recurring
workflow cron until the ingestion stage has been changed to invoke an incremental,
idempotent synchronization use case or the operational cost of the full rebuild has
been explicitly accepted.

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

Before switching the job to a recurring cron, implement an incremental ingestion stage
for the coordinator and add an end-to-end test that executes against MySQL with real
Quartz persistence and verifies interruption, lease expiry and resume.
