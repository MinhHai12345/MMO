# FB end-to-end content and publishing workflow

## Automated stages

1. Existing Quartz jobs refresh configured leagues, teams, fixtures/results, and match predictions.
2. `ContentWorkflowScheduledRunner` runs at 11:30 UTC by default, discovers upcoming matches for the next 15 days, and creates PRE_MATCH drafts through the existing content orchestrator.
3. The orchestrator runs analytics-backed draft generation and the content quality gate, then persists workflow runs, item checkpoints, and draft versions.
4. Existing editorial review endpoints keep approval explicit. A blocked or unreviewed draft cannot be used to create a publication.
5. An editor creates a publication using the publication API and an `Idempotency-Key`, then approves the publication.
6. `ContentPublicationWorker` polls due Telegram publications, checks the schedule/approval/attempt limit, and delegates sending to the official Telegram Bot API service.
7. Publication state and attempt history record the outcome. Failed attempts become eligible again after a configurable backoff, up to a bounded number of attempts.

## Configuration

The content-draft scheduler is enabled by default. It can be disabled with:

```properties
CONTENT_WORKFLOW_SCHEDULER_ENABLED=false
```

Live publishing is deliberately opt-in:

```properties
CONTENT_PUBLISHING_WORKER_ENABLED=true
CONTENT_PUBLISHING_API_KEY=<long-random-secret>
TELEGRAM_TOKEN=<bot-token>
TELEGRAM_CHANNEL_ID=<channel-id>
```

The API key protects the manual live-publish endpoint; the worker is enabled separately. Configure Telegram credentials and confirm the bot has permission to post to the channel before enabling the worker.

Optional worker settings:
- `app.content.publishing.worker.fixed-delay-ms` (default 30000)
- `app.content.publishing.worker.initial-delay-ms` (default 15000)
- `app.content.publishing.worker.batch-size` (default 10)
- `app.content.publishing.worker.max-attempts` (default 5)
- `app.content.publishing.worker.retry-delay-ms` (default 300000)

The worker currently publishes only TELEGRAM. Other platform enum values are not sent by this worker because they do not have a confirmed live adapter in this workflow.

## API sequence

1. Draft generation: `POST /api/fb/content/workflows/generate` (or allow the scheduled runner to create PRE_MATCH drafts).
2. Review the draft: `PATCH /api/fb/content/workflows/drafts/{draftId}/review` with status `APPROVED`.
3. Create a publication: `POST /api/fb/publish/publications` with an `Idempotency-Key` header and a body containing `draftId`, `platform=TELEGRAM`, `title`, `body`, optional `mediaReference`, optional `scheduledAt`, and `requestedBy`.
4. Approve the publication: `POST /api/fb/publish/publications/{publicationId}/approve` with `approvedBy` and optional `notes`.
5. If `scheduledAt` is null or due, the worker sends it on the next polling tick. Future schedules wait until due.
6. Inspect `GET /api/fb/publish/publications/{publicationId}` for state, external post ID, and attempt history.

## Reliability boundaries

- The DB row lock and state re-check prevent concurrent workers from processing the same publication row at the same time.
- Retries are bounded and delayed. Rows that reach the maximum attempt count require operator review; they are not retried forever.
- External messaging APIs cannot provide a universal exactly-once guarantee if the remote service accepts a message but the client loses the response. Check the Telegram channel and attempt details before manually resetting such a publication.
- This implementation is a runnable basic workflow, not a distributed queue or multi-platform publisher. Monitor scheduler logs and keep live publishing disabled until credentials and approval procedures are validated.
