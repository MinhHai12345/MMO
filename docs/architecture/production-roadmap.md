# MMO production roadmap

**Purpose:** define an implementation order that moves MMO from a collection of working modules to a reliable, observable football-data-to-content system.

**Planning baseline:** 2026-11-10. This is an engineering roadmap, not a claim that every listed capability is already production-ready. Re-check the code, migrations, CI and deployed runtime before changing a milestone to complete.

## Product and engineering objective

Build one dependable pipeline:

`Provider ingestion → canonical football data → analytics → verified content → channel package → publication → performance measurement`

The workflow orchestrator owns stage dependency, run identity, checkpoints and recovery. Domain services retain ownership of ingestion, analytics, content and publication behavior. Controllers and Quartz jobs remain thin entry points.

## Current baseline

The following capabilities are present in the repository and form the starting point for the roadmap:

- Durable workflow runs and per-stage checkpoints, with lease ownership and execution-token fencing.
- Quartz adapter for the durable coordinator. Its initial trigger is intentionally dormant/manual-only.
- The coordinator's data-ingestion stage uses incremental active-season synchronization. Historical backfill and match enrichment remain separate scheduled responsibilities.
- Data-readiness checks gate analytics and content generation.
- Existing advanced-metrics, upcoming-match prediction and pre-match content services are called in workflow order.
- Canonical content-claim provenance and publication persistence/attempt history are documented and represented in the codebase.
- Telegram is the currently wired live publishing provider; publishing is opt-in and requires editorial approval.
- Monetization event and campaign tracking foundations exist, but the available event streams and integrations do not yet prove complete audience or revenue attribution.

These statements describe repository capabilities. They do not certify production deployment, provider credentials, data quality, end-to-end recovery, or external delivery behavior.

## Prioritized implementation sequence

### P0 — Workflow reliability and runtime verification

**Goal:** every workflow run must either complete with trustworthy upstream data or stop with an actionable failure, and must resume without silently skipping required work.

Work:
1. Keep incremental active-season synchronization in the daily workflow; keep historical bootstrap/backfill and match enrichment on their own cadence.
2. Verify that lock contention, provider failures, partial ingestion and empty work plans cannot be recorded as successful freshness.
3. Add integration tests against MySQL and real Quartz persistence for interruption, lease expiry, fencing, restart and resume.
4. Verify checkpoint transitions and side-effect idempotency at each workflow boundary.
5. Add stage-level freshness metadata so downstream stages can distinguish a recent successful refresh from an old successful run.
6. Keep the end-to-end Quartz trigger manual-only until the integration and operational gates below pass.

**Exit criteria:**
- A failed or skipped ingestion cannot advance readiness, analytics or content.
- A second scheduler node cannot concurrently own the same workflow or ingestion scope.
- Restarting after interruption resumes completed/failed stages correctly.
- A workflow run and its stage history can be diagnosed from persisted state, not logs alone.
- Production scheduling is enabled only after a controlled rollout and measured runtime.

### P1 — Content lifecycle and factual integrity

**Goal:** generate the right content format at the right match lifecycle point, with traceable claims.

Work:
1. Define explicit lifecycle transitions for `PRE_MATCH`, `POST_MATCH`, `LONG_VIDEO` and `SHORTS`.
2. Define format-specific eligibility, timing, idempotency keys and regeneration/version rules.
3. Require structured provenance for material factual claims: source identity, source field/reference, source observation time when available, FB capture time, methodology and limitations.
4. Add automated validation for missing evidence, stale values, contradictory sources and unsupported claims.
5. Keep editorial approval as a distinct state transition; generation must never imply approval.

**Exit criteria:**
- Each lifecycle format has deterministic eligibility and duplicate-prevention rules.
- Claims can be traced to captured source evidence.
- Unverifiable claims are flagged or excluded rather than presented as facts.
- Draft versions and review history are retained.

### P2 — Channel production pipeline

**Goal:** turn an approved content draft into a validated, channel-specific publishing package.

Work:
1. Introduce a channel-neutral content package contract.
2. Add platform-specific copy constraints, captions, metadata and calls to action.
3. Add reusable graphics/templates driven only by validated match and analytics data.
4. Add media rendering behind an adapter boundary; store generated assets with stable references and metadata.
5. Validate dimensions, duration, file type, text bounds, captions and asset rights before a package is eligible for publication.

**Exit criteria:**
- The same approved editorial source can produce separate channel packages without duplicating factual logic.
- Every asset is linked to its source draft and provenance.
- Rendering failures are retryable and do not create duplicate packages.
- Rights and licensing checks are explicit release gates.

### P3 — Publishing reliability and platform expansion

**Goal:** safely distribute approved packages and make remote delivery outcomes auditable.

Work:
1. Keep a dispatcher/adapter contract separate from editorial and rendering logic.
2. Use an outbox/claim model, bounded retries, backoff, rate limits and per-platform idempotency.
3. Preserve ambiguous remote outcomes as `DELIVERY_UNKNOWN` when the provider cannot guarantee idempotency; require reconciliation rather than blind resend.
4. Complete Telegram operational safeguards before enabling more providers.
5. Add Facebook, X, YouTube and TikTok only after their API permissions, publishing constraints, media requirements and account eligibility are verified.

**Exit criteria:**
- Only approved and due packages are sent.
- Every attempt records request identity, provider response, external post reference and timestamps.
- Retry behavior cannot blindly duplicate an ambiguous delivery.
- Each platform has contract tests and a controlled live smoke test.

### P4 — Performance analytics and operations

**Goal:** understand whether content is useful and whether the system is healthy.

Work:
1. Persist publication-level performance observations with source, metric definition, observation time and capture time.
2. Track reach/engagement/clicks only where the platform provides supported data; do not infer unavailable metrics.
3. Build reports by channel, format, league, topic and publication cohort.
4. Add operational views for workflow runs, stage durations, stale data, provider failures, retry queues and delivery-unknown items.
5. Add actionable alerts and operator runbooks for stuck leases, repeated failures, provider quota exhaustion and publication reconciliation.

**Exit criteria:**
- Metrics are attributable to a specific publication and time window.
- Dashboards distinguish missing data from zero performance.
- Operators can locate, diagnose and safely recover failed work without direct database edits.

### P5 — Monetization and attribution

**Goal:** measure verified conversions and net revenue only after distribution and audience measurement are reliable.

Work:
1. Connect tracked campaign links to publication and channel identifiers.
2. Integrate supported audience/lead events with explicit deduplication and source semantics.
3. Accept payment and refund events only from authenticated, verified payment-provider notifications.
4. Report confirmed payments, refunds and net revenue by currency and campaign.
5. Validate event completeness against the upstream provider ledger before using reports for business decisions.

**Exit criteria:**
- Clicks, leads, subscriptions, payments and refunds are separate event types.
- Duplicate events do not inflate totals.
- Revenue reports include only verified payment/refund observations and disclose missing integrations.
- Commercial expansion is based on measured retention and contribution, not views or follower counts alone.

## Cross-cutting engineering gates

Every implementation milestone must satisfy:

- **Architecture:** Controller/Scheduler → application service/use case → repository/port; provider and platform integrations remain adapters.
- **Idempotency:** define the business key and duplicate behavior before adding retries.
- **Concurrency:** define ownership/locking and stale-worker fencing for shared resources.
- **Persistence:** review schema migrations and existing-data compatibility; do not rely on destructive schema recreation for production.
- **Testing:** JUnit 5/Mockito for application logic; MySQL-backed integration tests for locking, transactions and persistence semantics; contract tests for external adapters.
- **Observability:** structured logs, run/stage identifiers, durations, counts, failure classification and freshness.
- **Safety and editorial integrity:** no unsupported statistics, fabricated claims or unlicensed media. Editorial approval remains explicit.
- **Terminology:** use football analytics and data-science terminology consistently.

## Delivery and rollout policy

1. Implement one milestone at a time in a focused branch and pull request.
2. Inspect the current `main` branch before changing an existing module; avoid parallel implementations of the same responsibility.
3. Run `mvn -B -ntp clean verify` in CI and resolve failures before merge.
4. Deploy behind configuration flags where external side effects are involved.
5. Verify database migrations, provider quotas, credentials, API permissions and rollback procedures in a non-production environment.
6. Enable recurring workflow and live publishing only after their respective exit criteria pass.

## Pull-request numbering

The milestone labels in planning discussions (for example, roadmap items `#226`–`#234`) are planning identifiers only. GitHub assigns actual pull-request numbers sequentially; the roadmap labels must not be interpreted as GitHub PR numbers. Always link the actual PR URL when recording implementation status.

## Immediate next actions

1. Complete MySQL/Quartz integration coverage for durable workflow recovery.
2. Audit and test stage freshness and ingestion failure semantics end-to-end.
3. Finalize lifecycle formats and provenance validation before adding more media or social platforms.
4. Build the channel package and publishing contract around the existing persistence model.
5. Add performance measurement and operational dashboards before treating monetization reports as complete.
