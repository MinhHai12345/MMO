# FB end-to-end content and publishing workflow

## Runtime architecture

The workflow is scheduled by the project's existing persisted Quartz CronJob registry (FBJobGroup). It does not introduce a second content-only cron schedule.

1. FootballWorkflowOrchestrationJob runs daily at 11:30 UTC from FBCronjobInitial.
2. It triggers the existing FootballDataBootstrapJob only when bootstrap has never succeeded, its last run failed, or the last successful run is at least 30 days old. This avoids repeatedly backfilling configured historical seasons every day.
3. If bootstrap was skipped, it refreshes LeagueSyncJob when its last successful run is at least 30 days old and TeamSyncJob when its last successful run is at least 15 days old. It then triggers and waits for the existing jobs in dependency order: MatchResultSyncJob → MatchFixtureSyncJob → MatchStatisticsSyncJob → AdvancedMetricsJob → MatchProcessPredictionJob.
4. The orchestrator checks each job's persisted lastExecutedTime, jobStatus, and lastCompleteStatus. It proceeds only after a new execution is marked COMPLETE/SUCCESS; failure or timeout stops the pipeline before content generation.
5. After analytics/predictions finish, the workflow discovers upcoming matches in the next 15 days and creates PRE_MATCH drafts through the existing content lifecycle/orchestrator. The workflow run, item checkpoints and draft versions remain persisted and idempotent by UTC date.
6. Editorial approval remains an explicit human gate. The workflow does not auto-approve drafts or publish unreviewed content.
7. When explicitly enabled, PublicationWorkerQuartzJob is registered in the same persisted FBJobGroup CronJob registry and polls approved, due Telegram publications on its configured UTC cron expression. It delegates delivery to the Telegram adapter and records attempt outcomes.

The orchestrator uses the existing CronJob definitions by name in FBJobGroup; those definitions must be initialized by FBCronjobInitial. Each stage waits up to FB_WORKFLOW_JOB_TIMEOUT_MS (default 30 minutes). DisallowConcurrentExecution prevents concurrent executions of the same orchestrator job detail.

## Configuration

The orchestration job is registered with the existing cron-job registry at UTC 11:30. Its per-stage timeout is configurable:

```properties
FB_WORKFLOW_JOB_TIMEOUT_MS=1800000
```

Live publishing is opt-in and disabled by default. The worker schedule is managed through the same persisted CronJob registry:

```properties
CONTENT_PUBLISHING_WORKER_ENABLED=true
CONTENT_PUBLISHING_API_KEY=<long-random-secret>
TELEGRAM_TOKEN=<bot-token>
TELEGRAM_CHANNEL_ID=<channel-id>
```

The API key protects the manual live-publish endpoint; enabling the worker is a separate switch. When disabled, its persisted registry trigger is moved to a dormant annual expression and manual invocations are guarded. On startup, the worker's schedule is reconciled from configuration. Configure Telegram credentials and confirm the bot has permission to post to the target channel before enabling it.

Worker settings:
- Publication worker schedule is defined in `FBCronjobInitial` (`0/30 * * * * ?`, every 30 seconds UTC); change `PUBLICATION_WORKER_CRON_EXPRESSION` in code to adjust it.
- CONTENT_PUBLISHING_WORKER_BATCH_SIZE (default 10)
- CONTENT_PUBLISHING_WORKER_MAX_ATTEMPTS (default 5)
- CONTENT_PUBLISHING_WORKER_RETRY_DELAY_MS (default 300000)

Only Telegram is wired to a live provider in this worker. Other platform enum values are not sent by this workflow.

## API sequence

1. The existing cron workflow creates drafts automatically. Drafts can also be generated manually through POST /api/fb/content/workflows/generate.
2. Review a draft: PATCH /api/fb/content/workflows/drafts/{draftId}/review with status APPROVED.
3. Create a publication: POST /api/fb/publish/publications with an Idempotency-Key header and a body containing draftId, platform=TELEGRAM, title, body, optional mediaReference, optional scheduledAt, and requestedBy.
4. Approve the publication: POST /api/fb/publish/publications/{publicationId}/approve with approvedBy and optional notes.
5. When scheduledAt is null or due, the publication worker sends it on the next trigger. Future schedules wait until due.
6. Inspect GET /api/fb/publish/publications/{publicationId} for state, external post ID and attempt history.

## Reliability boundaries

- Existing CronJob definitions remain the source of truth for ingestion and analytics schedules. The orchestrator triggers those jobs and waits for their persisted completion state instead of assuming that a cron expression earlier in the day means a job succeeded.
- Bootstrap can import configured historical seasons, so it is only forced when stale or unsuccessful. The daily flow always refreshes recent results, upcoming fixtures, finished-match statistics, advanced metrics and predictions; league/team metadata are refreshed if stale.
- If an existing job is missing from the persisted FBJobGroup registry, orchestration fails fast rather than silently skipping that stage.
- The worker only queries due Telegram rows that have been approved and remain below the attempt limit; the publishing service locks and rechecks each row before sending.
- Retry is bounded and delayed. Rows that reach the maximum attempt count require operator review.
- Remote Telegram delivery and local database persistence cannot form one atomic transaction. If Telegram accepts a message but the response is lost, exactly-once delivery cannot be guaranteed; inspect the channel and attempt history before retrying ambiguous outcomes.
- This is a persisted workflow, not a distributed queue or multi-platform publisher. Validate provider credentials, the existing CronJob registry and editorial procedures in a non-production environment before enabling live publishing.
