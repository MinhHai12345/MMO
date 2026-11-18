# Multi-Provider Football Data Platform — Architecture & Delivery Standard

> Status: proposed target architecture for the existing MMO modular monolith  
> Baseline reviewed: `main` at tree `ff9b17b456c9e856eea918fefbdbc875ff6799d3`  
> Scope: architecture decisions and phased delivery rules; this document does not authorize a database reset or a rewrite.

## 1. Executive decision

MMO will remain **one Spring Boot modular monolith backed by MySQL**. Do not create a second repository, microservice, message broker, generic campaign engine, or parallel canonical schema as the next step.

The repository already contains substantial building blocks for multi-provider ingestion and durable orchestration:

- Provider ports and `ProviderIngestionRegistry` provide capability-specific adapter lookup.
- `DataSourcePolicy` routes a dataset by season and provider.
- `IngestionPlanFactory`, `IngestionPlan`, `IngestionWorkUnit`, and `IngestionUnitExecutor` model and execute provider/dataset/league/season work units.
- `ProviderSyncCheckpoint` persists provider/resource status and resumable cursor/counters.
- `IngestionDistributedLock` uses MySQL advisory locks for cross-instance exclusion.
- `FootballWorkflowRunEntity`, `FootballWorkflowStageExecutionEntity`, `WorkflowCheckpointService`, and `WorkflowLeaseHeartbeat` provide workflow-level idempotency, leases, stage checkpoints, and resume behavior.
- Canonical entities and provider external-ID tables already exist. Match statistics source snapshots and match field provenance also exist.

The objective is therefore to **evolve and consolidate the existing implementation**, not replace it with a second orchestration framework.

## 2. Repository baseline and boundaries

The baseline is a Java 17 / Spring Boot 3.4.5 application using Spring Data JPA, MySQL, Quartz, Spring Batch, WebFlux/WebClient, Playwright, Thymeleaf, Telegram integrations, and JUnit 5 / Mockito through Spring Boot Test.

The football application is organized as modules under `com.mmo.module.fb`:

| Module | Existing responsibility | Architectural boundary |
|---|---|---|
| `domain` | Canonical League, Season, Team, Match, Player and their persistence models | Must not depend on provider DTOs or scraper implementations |
| `ingest` | Provider adapters, ports, routing, normalization/persistence orchestration, checkpoints | Owns external data acquisition and canonical ingestion |
| `workflow` | Durable orchestration of ingestion, readiness, metrics, predictions and content stages | Coordinates use cases; must not implement provider parsing or statistical formulas |
| `analytics` | Feature extraction, statistical engines, data quality/readiness and match analysis | Consumes canonical data and provenance, not provider HTTP clients |
| `content` | Draft generation, content workflow, quality gate and lifecycle | Consumes approved analytics/readiness outputs |
| `publish` | Publication state machine, provider publisher adapters and publication worker | Publishes approved content; must not recalculate analytics |
| `monetization` | Subscription, conversion and revenue-related application functions | Separate business capability; outside ingestion ownership |

Core infrastructure under `com.mmo.core` supplies shared configuration, Quartz support, auditing, import utilities and framework integration. New football features should not be placed in the legacy generic core package unless they are genuinely cross-module infrastructure.

### 2.1 Known repository constraints

- `spring.jpa.hibernate.ddl-auto` defaults to `validate`; Quartz schema initialization is disabled. The inspected repository tree contains no checked-in SQL migration scripts and the inspected `pom.xml` has no Flyway or Liquibase dependency. Database schema evolution must therefore be treated as an explicit deployment concern; do not assume entity annotations automatically migrate an existing database.
- The project has both a generic core Quartz framework and football-specific Quartz adapters. Keep Quartz jobs thin and delegate to application use cases.
- `application.yml` contains provider enablement and credentials/routing defaults. Secrets must remain environment supplied; never commit real credentials.
- Some older documentation describes a different/earlier schema and includes legacy field names. This document and the current Java entities are the architecture baseline; older documents must be reconciled before they are used as implementation instructions.
- The current source tree includes legacy monetization/publication concepts. This architecture document does not authorize expanding or copying unrelated legacy behavior into ingestion or analytics.

## 3. Target architecture

The target remains a modular monolith with strict inward dependency direction:

```text
Quartz / REST / CLI / Startup
          |
          v
Inbound adapters (thin)
          |
          v
Application use cases and orchestration
          |
          +------ Provider ports ------> Provider adapters
          |                                (SofaScore, Understat,
          |                                 Football-Data, Football-Data UK,
          |                                 API-Football, future sources)
          |
          +------ Persistence ports ---> JPA / MySQL adapters
          |
          v
Canonical domain + source provenance
          |
          v
Analytics -> Readiness -> Content -> Publication
```

Rules:

1. Controllers, Quartz jobs and startup runners only validate/translate requests and invoke use cases.
2. Provider adapters own HTTP/browser access, provider-specific pagination, rate limits, response DTOs and provider-to-neutral mapping. They do not write canonical JPA entities.
3. Application services own orchestration, identity resolution, source selection and transaction boundaries through ports.
4. Persistence adapters own JPA queries and writes. They do not decide which provider is authoritative.
5. Domain entities remain provider-neutral. Provider identifiers and observations are represented by mapping/provenance records.
6. Analytics consumes canonical domain projections and explicitly identified source measurements. It must not call providers directly.
7. Content and publication consume persisted analytics/content artifacts; they do not reach back into provider adapters.

## 4. Provider integration contract

Keep the existing capability-specific ports and registry. Do not replace them with one large provider interface that forces every provider to implement unsupported capabilities.

A provider adapter should expose only capabilities it actually supports, for example:

- League catalogue discovery
- Season discovery
- Team discovery for a league-season
- Fixture/result retrieval
- Standings
- Match/team statistics
- xG or other advanced measurements
- Player and lineup statistics
- Historical file import

A provider without a capability must be represented as unsupported/not available, not as a successful empty dataset. Discovery output must preserve provider status, warnings and errors.

### 4.1 Provider adapter responsibilities

Each adapter is responsible for:

- Provider-specific transport and authentication.
- Rate limiting, retryable transport classification and bounded timeouts.
- Provider DTOs and parsing.
- Pagination and source cursor semantics.
- Mapping provider payloads into neutral `Provider*` application models.
- Returning explicit outcomes for unavailable, restricted, partial and failed responses.

Adapters must not:

- Choose canonical ownership for a dataset.
- Create or merge canonical entities by display name.
- Update analytics fields directly.
- Swallow provider failures and return an empty list as if it were a valid empty catalogue.
- Depend on controller, Quartz or workflow classes.

### 4.2 Provider identity

Provider IDs are strings at the application boundary unless a provider contract proves a narrower type is safe. Preserve leading zeros and non-numeric identifiers. Do not parse every provider ID as `Long`.

Provider identity is scoped by provider and entity type. A provider ID must never become an MMO primary key or canonical code.

## 5. Canonical domain and identity resolution

MMO owns canonical identity. Provider names, slugs, IDs and season labels are source attributes, not canonical identity.

Existing identity models must be reused:

- `LeagueExternalId`
- `SeasonExternalId`
- `TeamExternalId`
- `MatchExternalId`
- `PlayerExternalId`

Do not introduce a generic `provider_entity_mappings` table that duplicates these established mappings without a demonstrated cross-entity requirement and a migration plan.

Identity rules:

1. Resolve provider identity to canonical identity before writing fixtures, statistics or analytics.
2. Use stable MMO-owned canonical codes for League and Team; display names are not identity keys.
3. Season identity is scoped to its canonical League and normalized season label/date interval.
4. Match identity must include the canonical season, home team, away team and kickoff identity strategy; account for kickoff corrections and reversed provider home/away orientation explicitly.
5. Never auto-merge teams or leagues by normalized name alone. Ambiguous records go to a review/reconciliation path.
6. External-ID unique constraints are correctness controls, not a substitute for identity resolution.
7. Team participation in a competition is seasonal; do not make a permanent team-to-league relationship the identity rule.

Match identity reconciliation must build on the existing external ID repositories and the match identity reconciliation documentation. Duplicate candidates must be classified and reviewed before destructive merges or reassignment.

## 6. Source observations, raw payload retention and canonical projection

MMO already has source-retention patterns, but they are not yet a universal raw ingestion archive:

- `MatchStatisticsSourceSnapshot` stores serialized provider statistics payloads for a match/team/provider.
- `MatchFieldProvenance` records the provider and dataset responsible for a canonical match field.
- `MatchEnrichmentState` tracks resource-level enrichment state.

These are valuable and must be preserved. Do not claim they cover every provider response, entity type or ingestion stage.

### 6.1 Incremental target

Introduce a generalized provider observation/raw payload capability only after a focused design and storage-cost review. It should support, where required:

- Provider and entity/dataset identity.
- Provider external ID and resolved canonical reference when available.
- Fetch timestamp and source update timestamp.
- Request/correlation metadata without credentials or sensitive headers.
- Content type, schema/adapter version and payload hash.
- Raw payload bytes/text or a documented object-storage reference.
- Parse/normalization status and failure reason.
- Retention policy and replay eligibility.

The raw record is immutable after capture. Re-fetching the same entity creates a new observation or is deduplicated by a deterministic idempotency key; it must not silently rewrite historical source evidence.

Do not store secrets, authorization headers, cookies, browser profiles, or unnecessary personal data in raw payload records. Define retention, compression, payload size limits, indexes and archival policy before enabling broad capture.

### 6.2 Canonical projection

Use typed provider DTO -> neutral provider model -> identity resolution -> validated canonical projection.

For every field with multiple possible providers, define:

- Canonical field and units/definition.
- Eligible providers and season/competition scope.
- Primary source and allowed fallback.
- Null/blank handling.
- Freshness rule.
- Conflict policy.
- Provenance update behavior.
- Manual-review condition.

A provider priority is field-specific. Do not declare one provider universally authoritative for all match, team, player and competition attributes. Statistics with different definitions or collection methodologies must remain distinguishable; do not combine them as if they were equivalent.

## 7. Source routing policy

Keep `DataSourcePolicy` as the single application-owned routing authority. Provider adapters must not implement global source ownership decisions.

The current implementation uses season start year and a league-name allow-list for Football-Data UK coverage. The current documented routing behavior includes:

- Football-Data UK for supported historical core seasons from 2014 through 2022.
- Football-Data for core seasons from 2023 onward.
- SofaScore as a permitted secondary/fallback source for selected core/statistics datasets.
- Football-Data UK as the primary historical match-statistics/xG source where supported, with configured SofaScore/Understat alternatives.
- SofaScore for player statistics.
- Standings and upcoming odds have narrower/currently limited ownership.

This is a description of the inspected implementation, not a permanent universal truth. Keep routing decisions covered by unit tests and update this document whenever policy changes.

Required improvements before expanding routing:

1. Replace fragile display-name-only coverage checks with canonical league code or explicit provider coverage metadata.
2. Represent unsupported capability separately from temporary provider failure and valid empty response.
3. Make fallback eligibility explicit per dataset and season.
4. Preserve primary ownership: a secondary source must not overwrite fields owned by a primary source merely because it returned later.
5. Add route explainability to diagnostic output: selected primary, fallback order, reason, and capability support.
6. Treat source priority changes as reviewed data-policy changes, not incidental adapter refactors.

## 8. Durable ingestion execution: reuse before extending

MMO currently has two related levels of execution state:

### Resource/work-unit level

`IngestionPlanFactory` creates deterministic `IngestionWorkUnit` values by dataset and season. `IngestionUnitExecutor` applies checkpoint state and a scoped distributed lock. `ProviderSyncCheckpoint` stores provider + checkpoint key, status, cursor and row counters.

### End-to-end workflow level

`FootballWorkflowCoordinator` executes daily stages using a stable idempotency key. `WorkflowCheckpointService` owns run/stage state, execution token and lease. `WorkflowLeaseHeartbeat` renews the lease while a synchronous stage runs.

These responsibilities must remain distinct:

- Workflow run: a business-level pipeline execution.
- Workflow stage: a coarse step such as data ingestion or data readiness.
- Ingestion work unit: one provider/dataset/league/season operation.
- Provider checkpoint: resumable progress for that work unit.
- Publication attempt: delivery of an already-approved content item.

### 8.1 Do not add a parallel campaign/task framework yet

Do not add generic `sync_campaigns`, `sync_tasks`, `sync_executions` or a second worker queue as a first implementation step. Such a framework would overlap with existing workflow runs, work units, checkpoints and locks.

First complete an execution-state audit and establish whether the existing model can meet these requirements:

- Independent retry of one failed work unit without rerunning unrelated datasets.
- Configurable backoff and maximum attempts.
- Persisted last error, attempt history and next eligible run time.
- Safe stale-running recovery and lease fencing.
- Per-unit progress and observability.
- Operator-triggered replay of a selected provider/dataset/season.
- Bounded concurrency and provider-specific rate limits.
- No duplicate canonical writes under concurrent provider execution.

If a requirement cannot be represented cleanly, extend the existing model with the smallest additive change. Introduce a separate task ledger only when a concrete requirement (queue-based dispatch, independent worker deployment, high-volume fan-out, or per-task scheduling) cannot be met without it. The design must document ownership and migration from existing checkpoints first.

### 8.2 Execution invariants

- Every work unit has a deterministic identity.
- Every retry is safe to execute again.
- A checkpoint is marked completed only after the corresponding persistence transaction succeeds.
- `NOT_AVAILABLE` is distinct from `COMPLETED`, `PARTIAL` and `FAILED`.
- Retryable transport/database errors are distinguishable from permanent data/validation errors.
- A worker that loses its lease/token cannot mark a stage complete.
- Distributed lock release failure must not return a still-locked database session to the connection pool.
- Historical backfill and active-season refresh must have different checkpoint semantics; a historical completion must not suppress scheduled refresh.
- Do not hold a database transaction open across long browser/network operations.
- Batch persistence must have explicit chunk boundaries and bounded memory use.

## 9. Scheduling and API control plane

Quartz remains the scheduler in the current modular monolith. Quartz jobs are triggers, not business orchestrators.

REST endpoints are the control plane for diagnostics and operator commands. They should:

- Validate input and authorization.
- Call an application use case.
- Return a stable execution/report identifier and status.
- Avoid directly performing long-running provider calls in the HTTP request thread.
- Expose read-only status/diagnostic endpoints separately from mutating operations.

A future asynchronous API may submit a durable execution request and return `202 Accepted`, but this must reuse the existing execution identity and status model unless the design review approves a separate task ledger.

Protect all ingestion operations with explicit authorization. Do not expose unrestricted provider discovery, rebuild, reset or replay endpoints. Destructive actions require environment allow-listing, verified backup, explicit confirmation and an auditable operator identity.

## 10. Data quality and analytics contract

Analytics is downstream of canonical ingestion and readiness. It must not infer completeness from row counts alone.

Readiness should evaluate by league-season and by dataset:

- Fixture count and canonical identity coverage.
- Finished/scheduled status consistency.
- Score completeness.
- xG coverage and source provenance.
- Team/match advanced-statistics coverage.
- Player/lineup coverage where the analysis requires it.
- Source freshness and conflicting observations.
- Duplicate identity candidates and unresolved mappings.

A readiness gate must report both a machine-readable status and actionable reasons. A failed readiness gate blocks downstream computation for the affected scope; it must not silently produce misleading metrics.

Statistical engines (Poisson, Dixon-Coles, form/EMA, team strength and derived indices) belong in `analytics`. Each model must document its inputs, training window, parameter estimation, missing-data behavior, calibration/evaluation protocol and version. A model must not imply reliability merely because it returns a probability distribution.

## 11. Content and publication lifecycle

Keep the existing separation:

1. Analytics produces persisted, versioned analysis outputs.
2. Content generation creates a draft with traceable source claims.
3. Quality gates validate data readiness, numeric claims, provenance and editorial constraints.
4. An editor/approval transition authorizes publication.
5. Publication worker sends approved/due content through a platform adapter.
6. Publication attempts and external delivery identifiers are persisted for reconciliation.

Publishing must not run from a raw analytics controller or from a provider ingestion adapter. Unknown delivery outcomes must be reconciled before retry to avoid duplicate posts. Content must clearly distinguish observed facts, derived statistics and model estimates.

## 12. Proposed package ownership

This is an ownership guide, not an instruction to move all existing classes immediately.

```text
com.mmo.module.fb
  domain/
    league/ team/ match/ player/
  ingest/
    adapter/in/          # REST, Quartz, startup triggers
    adapter/out/provider/# provider clients, DTOs, mappers
    adapter/out/persistence/
    application/         # use cases, orchestration, identity, ports
    policy/              # dataset/provider source routing
  workflow/
    application/         # end-to-end workflow coordination
    domain/              # run/stage state
    infrastructure/      # workflow persistence
  analytics/
    engine/ service/ repository/
  content/
    generator/ service/ quality/provenance/
  publish/
    provider/ service/ domain/
  monetization/
```

Avoid package-wide rewrites solely to match this diagram. Move a class only when a concrete dependency or ownership defect is being fixed, and keep each refactor behavior-preserving.

## 13. Phased delivery roadmap

Each phase should be a small, reviewable PR with tests and an operational rollback plan. Do not combine all phases into one migration.

### Phase 0 — Baseline and architecture guardrails

- Reconcile this document with the existing production roadmap, master-data standards, API docs and operational runbook.
- Inventory all provider capabilities, source routes, persistence adapters, checkpoints, Quartz jobs and public endpoints.
- Capture current schema/table constraints and backup/restore procedure.
- Establish a repeatable clean MySQL integration-test environment.
- Document known legacy/duplicate paths and decide which are still active.

Exit criteria: one verified inventory; no new ingestion architecture is introduced before it exists.

### Phase 1 — Provider and routing correctness

- Finish provider catalogue discovery across supported adapters.
- Resolve the open league-catalogue discovery work without regressing existing providers.
- Add capability/status contracts and adapter contract tests.
- Replace display-name routing assumptions with canonical league coverage metadata.
- Add explainable route decisions and fallback tests.
- Verify provider IDs are treated as opaque strings.

Exit criteria: every configured provider capability has a tested support contract; unsupported, empty, restricted and failed responses are distinguishable.

### Phase 2 — Identity and canonical projection

- Audit all five external-ID mapping families and unique constraints.
- Complete match identity reconciliation and kickoff/home-away correction rules.
- Add reviewed aliases for sources that identify teams by names (for example historical CSV imports).
- Extend field-level provenance beyond the match fields already covered where analytics requires it.
- Define explicit source policies for non-match canonical attributes.
- Ensure persistence adapters are idempotent and do not overwrite curated canonical values with blanks.

Exit criteria: replaying a provider dataset cannot create duplicate canonical entities or silently move a provider ID between entities.

### Phase 3 — Execution reliability and observability

- Audit the existing `IngestionWorkUnit` / checkpoint / distributed-lock flow and workflow run/stage lease flow together.
- Add independent work-unit attempt history and retry scheduling only if the current checkpoint model cannot support the required operations.
- Verify lease fencing, stale-run recovery, lock release, cancellation and restart behavior.
- Add provider rate limiting, concurrency limits and structured execution reports.
- Provide operator APIs for status, retry/replay and diagnostic inspection with authorization.

Exit criteria: a failed provider/season can be retried independently; a restarted process does not duplicate canonical data or lose execution ownership.

### Phase 4 — Raw observation archive

- Finalize payload scope, privacy, retention, storage cost and replay policy.
- Add append-only raw observation storage for datasets that need audit/reprocessing.
- Link observations to provider identity, fetch execution, canonical entity and normalizer version.
- Implement deterministic replay into neutral models and canonical projections.
- Preserve existing match-statistics snapshots; migrate or consolidate only after data parity is proven.

Exit criteria: selected datasets can be re-normalized from retained source observations without refetching the provider; retention and storage are observable.

### Phase 5 — Data quality and analytics readiness

- Define per-dataset completeness contracts.
- Build repeatable season coverage audits.
- Resolve duplicate and missing identity candidates before analytics.
- Version analytics inputs/model parameters and persist evaluation metrics.
- Enforce readiness gates before daily metrics and content generation.

Exit criteria: analytics jobs operate only on explicitly ready scopes and publish quality reports.

### Phase 6 — Content and social publishing hardening

- Preserve claim-level provenance from analytics output to draft.
- Validate content numbers against persisted analysis artifacts.
- Require approval before live publication.
- Reconcile uncertain delivery states before retry.
- Add per-platform observability and safe retry rules.

Exit criteria: every published numeric claim can be traced to a canonical dataset and model version; duplicate delivery is controlled.

### Phase 7 — Scale only when measured

Consider external queue/worker services or separate deployables only when measured throughput, isolation, release cadence, or failure-domain requirements justify the operational complexity. The default remains a modular monolith.

## 14. Testing and quality gates

Required tests for changes:

- Provider adapter contract tests: parsing, pagination, missing fields, restricted access, empty response, retryable transport failure.
- Routing tests: dataset x season x league coverage x provider capability.
- Identity tests: idempotent insert, conflicting external IDs, ambiguous names, kickoff corrections and reversed orientation.
- Persistence tests: unique constraints, transaction boundaries, batch behavior and source precedence.
- Execution tests: concurrent claims, lock contention/release, lease expiry/fencing, retry, restart and partial failure.
- Readiness tests: coverage thresholds and explicit blocker reasons.
- Analytics tests: boundary conditions, normalization, missing data, deterministic outputs and backtest/evaluation.
- Content/publication tests: quality gate, approval state machine, unknown delivery state and duplicate prevention.

Run at minimum:

```bash
./mvnw -B -ntp clean verify
```

For MySQL-specific behavior (advisory locks, unique constraints, transaction isolation, query plans), include integration tests against MySQL; H2 alone is not sufficient evidence.

## 15. Database evolution and operational safety

- Do not drop, truncate or rebuild a live database as part of an ordinary feature PR.
- Do not assume Hibernate `ddl-auto=validate` performs migrations.
- Before a schema change, identify the actual schema-management process used by deployment. If no migration system exists, introduce and document one in a dedicated PR before relying on schema changes.
- Every schema change must include forward migration, rollback/recovery notes, index/constraint review and data backfill strategy.
- Backups must be restorable and verified before any destructive maintenance.
- Large raw payload tables require retention, partitioning/archive consideration and bounded query patterns.
- Use UTC instants for execution timestamps; preserve provider-local competition dates/time zones where they are part of source meaning.

## 16. Non-goals and prohibited shortcuts

- No immediate microservice split or new repository.
- No parallel generic campaign/task framework without a gap analysis.
- No second set of canonical League/Season/Team/Match tables.
- No generic mapping table that duplicates the current external-ID entities.
- No provider-specific fields or client dependencies in canonical domain entities.
- No name-only auto-merge.
- No provider-wide priority applied to every field.
- No long-running network/browser call inside a database transaction.
- No direct JPA writes from controllers, Quartz jobs or provider adapters.
- No destructive reset endpoint without the operational safeguards above.
- No use of gambling/betting terminology in code, variable names, logs, documentation or generated content. Use neutral statistical/data-science terminology such as expected goals, observed metrics, probability distribution, model estimate and data quality.

## 17. Definition of done for future implementation PRs

Every implementation PR must state:

1. Which phase and requirement in this document it implements.
2. Existing classes/tables reused and why new abstractions are necessary.
3. Provider/dataset/season scope and source ownership impact.
4. Idempotency and retry behavior.
5. Schema changes and deployment/backfill/rollback plan, or explicitly "no schema change".
6. Unit and integration tests added and commands/results.
7. Operational impact: scheduling, credentials, rate limits, logging and metrics.
8. Data quality/reconciliation impact.
9. Confirmation that no unrelated legacy flow or canonical data was silently removed.

A PR that introduces a new orchestration, mapping or raw-storage abstraction must include a comparison against the existing classes listed in Sections 5, 6 and 8 and justify why extension is insufficient.

## 18. Current implementation references

The following existing source files are architectural anchors and should be reviewed before changing their responsibilities:

- `ingest/application/registry/ProviderIngestionRegistry.java`
- `ingest/application/orchestration/IngestionPlanFactory.java`
- `ingest/application/orchestration/IngestionWorkUnit.java`
- `ingest/application/orchestration/IngestionUnitExecutor.java`
- `ingest/application/port/out/ProviderCheckpointPort.java`
- `ingest/adapter/out/persistence/entity/ProviderSyncCheckpoint.java`
- `ingest/adapter/out/persistence/core/ProviderCheckpointJpaAdapter.java`
- `ingest/application/lock/IngestionDistributedLock.java`
- `ingest/policy/DataSourcePolicy.java`
- `domain/*/entity/*ExternalId.java`
- `domain/match/entity/MatchFieldProvenance.java`
- `domain/match/entity/MatchStatisticsSourceSnapshot.java`
- `workflow/application/service/FootballWorkflowCoordinator.java`
- `workflow/application/service/WorkflowCheckpointService.java`
- `workflow/application/service/WorkflowLeaseHeartbeat.java`
- `analytics/service/DataQualityValidationService.java`
- `analytics/service/AnalyticsDataReadinessService.java`
- `content/service/ContentWorkflowOrchestrator.java`
- `publish/service/impl/ContentPublicationWorker.java`

This document is the forward-looking implementation contract. Existing production behavior is changed only by separately reviewed implementation PRs.
