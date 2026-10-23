# FB end-to-end content and publishing workflow

## Runtime architecture

This workflow uses the project's existing Quartz scheduler; it does not use Spring's `@Scheduled` mechanism.

1. The existing ingestion Quartz jobs refresh configured leagues, teams, fixtures/results and prediction data.
2. `ContentWorkflowQuartzJob` runs on the configured Quartz cron (11:30 UTC by default), discovers upcoming matches in the next 15 days and creates PRE_MATCH drafts using the existing content lifecycle/orchestrator.
3. The content orchestrator generates analytics-backed drafts, applies the quality gate and persists run/item checkpoints and draft versions.
4. Editorial approval remains an explicit human gate. The workflow does not auto-approve drafts or create live posts from unreviewed content.
5. An editor creates a publication with an `Idempotency-Key` and approves it through the publication API.
6. When explicitly enabled, `PublicationWorkerQuartzJob` polls due Telegram publications at a configurable interval. The worker rechecks publication eligibility and delegates sending to the Telegram Bot API adapter.
7. Publication state and attempt history record outcomes. Failed attempts become eligible again after a configurable backoff, up to a bounded attempt count.

The content job and publication job are separate Quartz jobs. `@DisallowConcurrentExecution` prevents overlapping executions of the same job detail. This does not guarantee global single execution across different job keys or independently deployed applications.

## Configuration

The content workflow Quartz trigger is enabled by default:

```properties
CONTENT_WORKFLOW_SCHEDULER_ENABLED=true
CONTENT_WORKFLOW_SCHEDULER_CRON=0 30 11 * * ?
```

Disable content draft generation with `CONTENT_WORKFLOW_SCHEDULER_ENABLED=false`.

Live publishing is opt-in and disabled by default:

```properties
CONTENT_PUBLISHING_WORKER_ENABLED=true
CONTENT_PUBLISHING_API_KEY=<long-random-secret>
TELEGRAM_TOKEN=<bot-token>
TELEGRAM_CHANNEL_ID=<channel-id>
```

The API key protects the manual live-publish endpoint; enabling the Quartz worker is a separate switch. Configure Telegram credentials and confirm the bot has permission to post to the target channel before enabling the worker.

Worker settings:
- `CONTENT_PUBLISHING_WORKER_FIXED_DELAY_MS` (default 30000)
- `CONTENT_PUBLISHING_WORKER_INITIAL_DELAY_MS` (default 15000)
- `CONTENT_PUBLISHING_WORKER_BATCH_SIZE` (default 10)
- `CONTENT_PUBLISHING_WORKER_MAX_ATTEMPTS` (default 5)
- `CONTENT_PUBLISHING_WORKER_RETRY_DELAY_MS` (default 300000)

Only Telegram is wired to a live provider in this worker. Other platform enum values are not sent by this workflow.

## API sequence

1. Generate drafts manually, if needed: `POST /api/fb/content/workflows/generate`. Otherwise the Quartz content job runs on its configured cron.
2. Review a draft: `PATCH /api/fb/content/workflows/drafts/{draftId}/review` with status `APPROVED`.
3. Create a publication: `POST /api/fb/publish/publications` with an `Idempotency-Key` header and a body containing `draftId`, `platform=TELEGRAM`, `title`, `body`, optional `mediaReference`, optional `scheduledAt`, and `requestedBy`.
4. Approve the publication: `POST /api/fb/publish/publications/{publicationId}/approve` with `approvedBy` and optional `notes`.
5. When `scheduledAt` is null or due, the Quartz publication worker sends it on the next trigger. Future schedules wait until due.
6. Inspect `GET /api/fb/publish/publications/{publicationId}` for state, external post ID and attempt history.

## Reliability boundaries

- The worker only queries due Telegram rows that have been approved and remain below the attempt limit; the publishing service locks and rechecks each row before sending.
- Retry is bounded and delayed. Rows that reach the maximum attempt count require operator review.
- Remote Telegram delivery and local database persistence cannot form one atomic transaction. If Telegram accepts a message but the response is lost, exactly-once delivery cannot be guaranteed; inspect the channel and attempt history before retrying ambiguous outcomes.
- Quartz jobs are the scheduling entry points. The content job assumes the existing ingestion/prediction jobs have already refreshed the data; its cron must be configured after those jobs' normal run windows. It does not synchronously invoke or wait for every ingestion job.
- This is a basic persisted workflow, not a distributed queue or multi-platform publisher. Validate migrations, provider credentials, Quartz triggers and editorial procedures in a non-production environment before enabling live publishing.
