# Football CronJob Audit and Monetization-Oriented Schedule

Audit scope: persisted CronJob definitions and job implementations on `main`, checked 2026-10-27. This document separates confirmed code findings from runtime behavior that requires a live database/provider to verify.

## Confirmed findings

| Job / area | Finding | Impact | Decision |
|---|---|---|---|
| `AdvancedMetricsJob` | Computes team form for every team in each current season, then H2H records for every ordered pair of teams. | The old `0 */2 * * * ?` Quartz expression means every **2 minutes**, not every 2 hours. This repeatedly runs an O(team²) workload. | Run every 6 hours; later optimize to incremental/team-pair queries. |
| `WalkForwardBackTestJob` | Runs a 180-day backtest and calibration. | Daily work is expensive and not on the critical path to draft creation. | Weekly, Sunday 06:15 UTC. |
| `MatchProcessPredictionJob` | Processes matches only from now + 6h through +54h; it skips existing predictions except PENDING / INSUFFICIENT_DATA. | A daily schedule can miss eligible matches; rerunning does not refresh predictions with a completed status. | Run every 3 hours; next improvement should add explicit prediction freshness/version policy. |
| `MatchFixtureSyncJob` | Refreshes fixtures from today to +14 days. | Daily cadence can leave stale kickoff/status information around busy match windows. | Run every 6 hours. |
| `MatchResultSyncJob` | Re-syncs the previous 7 days through today. | Daily cadence delays final-score correction and prediction evaluation. | Run every 4 hours. |
| `MatchStatisticsSyncJob` | Calls SofaScore bootstrap and the statistics crawl. | A full crawl every 4 hours can repeat expensive provider work. | Run every 6 hours; next improvement should checkpoint by match/stat freshness. |
| `MatchStatusTrackerJob`, `CrawlerHealthCheckJob`, `DataCleanupJob` | The current job implementations are empty. | They report successful executions without doing the work implied by their names. | Do not register them until implemented. |
| `MatchLineupOddsUpdateJob` | Fetches odds; this is not needed for the project's stated football-analysis content path and may encourage accidental betting-oriented coupling. | Quota and crawl cost with no clear value to the current content MVP. | Do not schedule in the default content workflow. Re-add only with an explicit approved use case. |
| `FBCronjobInitial` | Previously created definitions only when absent. | Changing a cron expression in code did not update existing persisted CronJob rows, so deployed installations could keep old schedules. | Reconcile persisted cron expression/class/description with code on startup. |
| `FootballWorkflowOrchestrationJob` | Sequentially triggers registered jobs and waits for persisted completion state. | Correctness depends on listener state updates and each child job reporting failures. Long polling occupies a Quartz worker. | Keep as the single end-to-end coordinator for now; improve execution tracking/timeout and per-stage status in a follow-up. |
| `AbstractJob` / `JobStateListener` | `AbstractJob` catches exceptions and writes ERROR to context; the listener persists completion state. | Errors can be obscured if a caller only watches logs; state transitions need tests. | Preserve the existing framework contract and add integration tests before changing failure propagation. |
| `ContentPublicationWorker` | Uses a bounded batch and attempt limits; it processes only due Telegram publications. | Good baseline safeguards, but actual API credentials, idempotency and remote failure handling must be verified in runtime. | Poll once per minute; keep publishing opt-in and bounded. |
| `SubscriptionExpiryCheckJob` | Loads all ACTIVE subscribers into memory and marks expired subscriptions; expiry notifications are TODO. | Unbounded memory use as subscribers grow; no reminder is actually sent. | Daily cadence remains acceptable for MVP; add paged queries and real notification/outbox before production scale. |

## Recommended job flow

1. **Data refresh:** fixtures and recent results, then statistics when due. Core/bootstrap backfill remains monthly/manual because it can touch many seasons and consume provider quota.
2. **Analytics:** refresh team form/H2H metrics, then compute upcoming predictions. Do not make content generation depend on a prediction when the match lacks sufficient data.
3. **Content:** discover matches in the next 15 days, generate a single draft per match/format according to the content lifecycle's idempotency policy, run factual quality checks, and leave drafts for review.
4. **Publishing:** a separate worker publishes only approved, due rows; it must remain opt-in, rate-limited, retry-bounded, and idempotent.
5. **Evaluation:** evaluate predictions after results arrive; run expensive walk-forward evaluation weekly, not in every content run.
6. **Revenue:** first collect content performance and subscriber/conversion events. Add paid membership/expiry notifications only when checkout, entitlement checks, renewal/cancellation, and notification delivery are wired end-to-end. Job frequency alone does not create revenue.

## Monetization priorities

- **P0 — Reliable distribution:** complete one channel (Telegram) with approval, deduplication, safe retries, pause/kill switch, external post ID, and publish status.
- **P0 — Useful repeatable content:** one or two leagues, match previews and post-match analysis, source-backed stats, factual quality gate, editorial review.
- **P1 — Audience measurement:** record per-post reach/clicks when available, use tracked links to a site/newsletter, and measure return visits and signups.
- **P1 — Small paid offer:** validate demand for a premium newsletter/report or membership before building a large paywall. Verify data and asset commercial-use rights first.
- **P2 — Scale:** expand leagues/providers/platforms only when retention, conversion and contribution margin justify the added quota and infrastructure cost.

Avoid betting-oriented output, fabricated statistics, unlicensed match footage, and mass low-value repetitive posts. Views/followers are not revenue; verify actual conversion and net contribution.

## Follow-up implementation gates

- Add tests for cron expression registration and update behavior, including an already-existing persisted job.
- Add tests that empty/no-op jobs are not registered.
- Add Quartz integration tests proving a child job's ERROR status causes the orchestrator stage to fail.
- Add job run IDs, duration, records processed, retry counts, provider quota usage, and last-success/freshness metrics.
- Make statistics and metrics incremental/checkpointed rather than rescanning all history.
- Page subscription expiry queries and send reminders via a durable outbox.
- Add publication-worker tests for disabled mode, due-time filtering, duplicate delivery, retry ceiling and platform errors.
- Verify the deployed scheduler, provider access, database state, and Telegram credentials in a real environment; repository inspection cannot prove these runtime dependencies are healthy.
