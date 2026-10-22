# Content Workflow Orchestrator

## Endpoint

`POST /api/fb/analytics/content-workflows/generate`

Optional query parameters:
- `from`, `to`: ISO-8601 instants; defaults to the discovery service's rolling seven-day window.
- `leagueId`: restrict discovery to one league.
- `limit`: number of candidates to process (default 20, maximum 50).
- `format`: `PRE_MATCH`, `POST_MATCH`, `LONG_VIDEO`, or `SHORTS` (default `PRE_MATCH`).

Example:

```http
POST /api/fb/analytics/content-workflows/generate?limit=10&format=PRE_MATCH
```

## Pipeline

1. Discover upcoming candidates using the existing social-insight discovery service.
2. Generate a draft for each candidate using the content-draft service.
3. Run the existing editorial quality gate through draft generation.
4. Return a batch report with candidate readiness, per-match draft status, counts, and warnings.

A failure for one fixture does not stop the rest of the batch.

## Status semantics

- `NO_CANDIDATES`: discovery returned no fixtures.
- `COMPLETED`: all drafts generated without review flags or failures.
- `COMPLETED_WITH_REVIEW`: one or more drafts need editor review or are blocked.
- `COMPLETED_WITH_ERRORS`: at least one fixture failed while others were processed.
- `FAILED`: every discovered fixture failed.

Per-item statuses: `READY_FOR_EDITOR_REVIEW`, `NEEDS_EDITOR_REVIEW`, `BLOCKED`, or `FAILED`.

## Current boundaries

- This is an orchestration skeleton, not a durable job queue.
- Drafts and run reports are returned synchronously and are not persisted.
- The endpoint does not publish content.
- Discovery readiness is data availability, not predicted result probability or model quality.
- `POST_MATCH` generation will be blocked for unfinished fixtures by the draft service; for post-match batch processing, a dedicated finished-match discovery mode should be added.
