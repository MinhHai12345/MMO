# FB end-to-end content and publishing workflow

## Runtime architecture

The workflow uses the project's persisted Quartz CronJob registry (`FBJobGroup`) and does not introduce a separate content-only scheduler.

1. `FootballWorkflowOrchestrationJob` runs daily at 11:30 UTC and is registered by `FBCronjobInitial`.
2. It runs `FootballDataBootstrapJob` when bootstrap has never succeeded, its last run failed, or its last successful run is at least 30 days old. This avoids repeatedly backfilling historical seasons every day.
3. When bootstrap is skipped, it refreshes `LeagueSyncJob` if its last success is at least 30 days old and `TeamSyncJob` if its last success is at least 15 days old.
4. It then runs and waits for these jobs in dependency order: `MatchResultSyncJob` → `MatchFixtureSyncJob` → `MatchStatisticsSyncJob` → `AdvancedMetricsJob` → `MatchProcessPredictionJob`.
5. Each stage is checked against the persisted `lastExecutedTime` and `lastCompleteStatus`. A failed stage or timeout stops the workflow before content generation.
6. After the data and analytics stages succeed, the workflow creates `PRE_MATCH` drafts for upcoming matches in the next 15 days. Workflow runs, item checkpoints and draft versions remain persisted and idempotent by UTC date.
7. Editorial approval remains a manual gate. The workflow does not auto-approve drafts or publish unreviewed content.
8. When enabled, `PublicationWorkerQuartzJob` polls approved, due Telegram publications through the same persisted CronJob registry and records attempt outcomes.

Each stage waits up to `FB_WORKFLOW_JOB_TIMEOUT_MS` (default 30 minutes). `@DisallowConcurrentExecution` prevents concurrent executions of the same orchestrator job detail.

## Configuration

The orchestration job is registered at 11:30 UTC. Its per-stage timeout is configurable:

```properties
FB_WORKFLOW_JOB_TIMEOUT_MS=1800000
```

Live publishing is opt-in and disabled by default:

```properties
CONTENT_PUBLISHING_WORKER_ENABLED=true
CONTENT_PUBLISHING_API_KEY=<long-random-secret>
TELEGRAM_TOKEN=<bot-token>
TELEGRAM_CHANNEL_ID=<channel-id>
```

The API key protects the manual live-publish endpoint; enabling the worker is a separate switch. When disabled, its persisted registry trigger is moved to a dormant annual expression and manual invocations are guarded. The publication schedule is defined in `FBCronjobInitial` by `PUBLICATION_WORKER_CRON_EXPRESSION` (`0/30 * * * * ?`, every 30 seconds UTC).

Worker settings:
- `CONTENT_PUBLISHING_WORKER_BATCH_SIZE` (default 10)
- `CONTENT_PUBLISHING_WORKER_MAX_ATTEMPTS` (default 5)
- `CONTENT_PUBLISHING_WORKER_RETRY_DELAY_MS` (default 300000)

Only Telegram is wired to a live provider in this worker. Other platform enum values are not sent by this workflow. Configure Telegram credentials and confirm that the bot can post to the target channel before enabling live publishing.

## API sequence

1. Drafts are created automatically by the scheduled workflow. They can also be generated manually through `POST /api/fb/content/workflows/generate`.
2. Review a draft through `PATCH /api/fb/content/workflows/drafts/{draftId}/review` with status `APPROVED`.
3. Create a publication through `POST /api/fb/publish/publications` with an `Idempotency-Key` header and a body containing `draftId`, `platform=TELEGRAM`, `title`, `body`, optional `mediaReference`, optional `scheduledAt`, and `requestedBy`.
4. Approve the publication through `POST /api/fb/publish/publications/{publicationId}/approve` with `approvedBy` and optional notes.
5. When `scheduledAt` is null or due, the worker sends the publication on its next trigger.
6. Inspect `GET /api/fb/publish/publications/{publicationId}` for state, external post ID and attempt history.

## Reliability boundaries

- Existing CronJob definitions remain the source of truth for ingestion and analytics schedules. The orchestrator triggers those jobs and waits for persisted completion rather than assuming that an earlier cron schedule means a job succeeded.
- Bootstrap can import configured historical seasons, so it only runs when stale or unsuccessful. The daily flow refreshes recent results, fixtures, statistics, advanced metrics and predictions; league/team metadata are refreshed when stale.
- If a required job is missing from the persisted `FBJobGroup` registry, orchestration fails fast instead of silently skipping the stage.
- The worker only queries due Telegram rows that have been approved and remain below the attempt limit. The publishing service locks and rechecks each row before sending.
- Retries are bounded and delayed. Rows that reach the maximum attempt count require operator review.
- Remote Telegram delivery and local database persistence cannot form one atomic transaction. If Telegram accepts a message but the response is lost, exactly-once delivery cannot be guaranteed; inspect the channel and attempt history before retrying ambiguous outcomes.
- This is a persisted workflow, not a distributed queue or multi-platform publisher. Validate provider credentials, the CronJob registry and editorial procedures in a non-production environment before enabling live publishing.
