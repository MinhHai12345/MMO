# Resumable FB Content Workflow

## Execution and idempotency

`POST /api/fb/content/workflows/generate` accepts an optional `Idempotency-Key` (maximum 128 characters). The workflow run is inserted with `RUNNING` and flushed before analytics/content generation starts. The unique database constraint is therefore the atomic claim: concurrent requests with the same key cannot both start generation.

- Same key and same normalized request: return the existing run.
- Same key with different filters/format/limit: HTTP 409.
- No key: each intentional request creates a new run.
- A duplicate-key race returns the run that won the claim.

## Per-match checkpoints

Each discovered match is tracked in `fb_content_workflow_items`, uniquely by workflow run and match ID.

- `RUNNING`: generation started; a process crash leaves a recoverable checkpoint.
- `COMPLETED`: draft and its initial version were persisted in the same transaction as the checkpoint.
- `FAILED`: generation failed and can be retried.
- `attemptCount` increments on each attempt; resume does not retry an item after 3 attempts.

Each successful item is persisted independently. A crash does not roll back already persisted drafts/checkpoints. Resuming skips completed matches and retries failed/running matches that remain under the attempt cap.

## API

- `POST /api/fb/content/workflows/generate` — start a workflow.
- `GET /api/fb/content/workflows/runs` — list runs and aggregate status.
- `GET /api/fb/content/workflows/runs/{runId}/items` — inspect per-match checkpoint status and failures.
- `POST /api/fb/content/workflows/runs/{runId}/resume` — resume failed/partial runs; a `RUNNING` run is recoverable only after it has been stale for 10 minutes.
- Existing draft and editorial-review endpoints are unchanged.

## Safety boundaries

This workflow does not publish automatically. Editorial approval remains required. A successful workflow status means generation/persistence completed; it does not guarantee that football data is complete or that an analytical claim is correct.

## Validation

Run `mvn clean compile` and `mvn test` in CI before merge. This implementation was authored through the repository connector; no local Maven execution has been performed in this environment.
