# MMO Target Architecture and Refactoring Standard

> Status: proposed mandatory architecture baseline
> Repository: MinhHai12345/MMO
> Baseline branch: main
> Baseline commit inspected: ff9b17b456c9e856eea918fefbdbc875ff6799d3
> Scope: target structure, current-code disposition, controlled removal plan and implementation sequence.

## 1. Executive decision

MMO will be refactored into a **modular monolith with explicit bounded contexts and pragmatic hexagonal architecture**. The project remains Java 17, Spring Boot 3.x and MySQL.

This is a restructuring of the existing product, not a greenfield rewrite and not a microservice migration.

The target product has six core capabilities:

1. Provider ingestion and source observation.
2. Canonical football master data and match data.
3. Data quality, identity resolution and provenance.
4. Statistical analytics and model evaluation.
5. Evidence-based content generation.
6. Controlled social publication.

Existing code is not automatically retained because it exists. Every component receives one disposition:

- **KEEP** — correct responsibility and suitable implementation.
- **REFACTOR** — capability is needed, but boundaries, implementation or reliability need improvement.
- **REPLACE** — current implementation is unsuitable; migrate consumers/data before removal.
- **REMOVE** — obsolete, duplicated, unrelated or prohibited capability; remove after consumer and data dependency checks.

Use the strangler pattern: build the new boundary around existing behavior, migrate one use case at a time, compare outputs, then remove the old path. No big-bang rewrite and no production data reset.

## 2. Repository baseline

The inspected main tree contains 552 tracked files, including 505 application Java files and 12 Java test files. Java source is concentrated in com.mmo.module.fb (453 files) and com.mmo.core (50 files).

The current Maven build uses Java 17 and Spring Boot 3.4.5. Relevant dependencies include Spring Data JPA, Spring MVC, Spring Data REST, Spring Batch, Quartz, WebFlux, MySQL, Playwright, Thymeleaf, Telegram, Twitter integration, Stripe, Commons Math and H2 tests.

Current football capabilities are distributed across:

- domain: League, Season, Team, Match, Player and provider identity entities.
- ingest: provider adapters, provider ports, routing, ingestion use cases, checkpoints, locks, master-data initialization and diagnostics.
- workflow: end-to-end workflow runs, stage execution, checkpoints and lease heartbeat.
- analytics: feature extraction, probability models, backtesting, readiness and data quality.
- content: drafts, versions, provenance, quality gate, workflow and video generation.
- publish: publication lifecycle, attempts, platform adapters and workers.
- monetization: subscriptions, campaign links, conversion events and payment integration.
- core: generic configuration, Quartz, import framework, converters, response wrappers and initialization utilities.

Important inconsistencies:

1. The root package remains com.mmo although the product is now a football analytics platform.
2. core contains generic legacy utilities and product-oriented import code mixed with infrastructure still needed by the football platform.
3. Some modules have service interfaces, implementation classes and overlapping orchestration services without a consistently enforced application-port boundary.
4. Canonical JPA entities live under domain packages, coupling persistence and domain concepts.
5. Ingestion has resource/work-unit checkpointing and end-to-end workflow stage checkpointing. Both are useful but their ownership must be explicit.
6. The repository has only 12 checked-in Java test files for 505 application Java files. Test coverage is not proportionate to the codebase.
7. The inspected source tree has no checked-in application SQL migration history and the inspected Maven dependencies do not include Flyway or Liquibase. Production documentation says Hibernate validates schema and production schema changes are applied separately. This process must become reproducible and version-controlled.
8. Several older documents describe earlier designs and schema contracts.
9. A README file was not present at the inspected root path. A proper project README is required.

This is a repository/tree and key-source review, not a claim that every line of all 505 Java files has undergone manual line-by-line review. The implementation phase must add dependency scans, usage checks and tests before deleting or moving individual classes.

## 3. Target architecture principles

### 3.1 Modular monolith first

- One deployable Spring Boot application.
- One primary MySQL database with module-owned tables and explicit ownership.
- Modules communicate through application interfaces or published module APIs.
- No module accesses another module's repositories or persistence entities directly.
- No shared mutable entity graph across module boundaries.
- No distributed messaging infrastructure until measured workload requires it.

### 3.2 Pragmatic hexagonal architecture

Each business module follows:

- domain: business concepts, invariants, domain services and domain events; no Spring, JPA, HTTP or provider SDK dependency.
- application: use cases, transaction boundaries, input/output ports and orchestration.
- adapter.in: REST, Quartz, CLI or startup adapters.
- adapter.out: database, provider client, file/object storage and external platform adapters.
- configuration: module-specific dependency wiring only where needed.

Controllers and scheduled jobs are thin inbound adapters. Provider clients and JPA repositories are outbound adapters. Business decisions live in application/domain code.

### 3.3 Dependency direction

~~~text
REST / Quartz / CLI
        |
        v
Inbound adapters
        |
        v
Application use cases -------> Domain
        |                        ^
        |                        |
        +---- output ports -------+
                 ^
                 |
       Outbound adapters
       (JPA / HTTP / browser / file)
~~~

A domain module must not depend on an adapter. An application service must not depend on a concrete provider client or Spring Data repository. Cross-module calls go through an application API/port, not by reaching into another module's internals.

### 3.4 Explicit data ownership

- Master data owns canonical league, season, team and player identities.
- Match data owns fixtures, match state, lineups and match observations.
- Ingestion owns provider acquisition, execution state, raw source observations and canonical projection requests.
- Analytics owns feature definitions, model versions, estimates and evaluation.
- Content owns editorial drafts and claim provenance.
- Publication owns delivery lifecycle and platform-specific delivery attempts.
- Workflow coordinates use cases but does not duplicate the state machine owned by each module.

## 4. Target package structure

This is a logical target. Refactor incrementally; do not move every file in one PR.

~~~text
com.mmo
  MmoApplication.java
  bootstrap/
    ApplicationConfiguration.java

  shared/
    domain/
    application/
    infrastructure/
      persistence/
      web/
      observability/

  module/fb/
    masterdata/
      domain/
        league/
        season/
        team/
        player/
        identity/
      application/
        port/in/
        port/out/
        service/
      adapter/
        in/web/
        out/persistence/

    match/
      domain/
        fixture/
        result/
        lineup/
        statistics/
        provenance/
      application/
        port/in/
        port/out/
        service/
      adapter/
        in/web/
        out/persistence/

    ingestion/
      domain/
        execution/
        checkpoint/
        source/
        observation/
      application/
        port/in/
        port/out/
        service/
        orchestration/
        policy/
      adapter/
        in/web/
        in/scheduler/
        out/provider/
          sofascore/
          understat/
          football-data/
          football-data-uk/
          api-football/
        out/persistence/
        out/browser/
        out/storage/

    analytics/
      domain/
        feature/
        model/
        evaluation/
        readiness/
      application/
        port/in/
        port/out/
        service/
      adapter/
        in/web/
        in/scheduler/
        out/persistence/

    workflow/
      application/
        port/in/
        port/out/
        service/
      adapter/
        in/scheduler/
        out/persistence/

    content/
      domain/
        draft/
        template/
        claim/
        approval/
      application/
        port/in/
        port/out/
        service/
      adapter/
        in/web/
        in/scheduler/
        out/persistence/
        out/llm/
        out/video/

    publication/
      domain/
        publication/
        delivery/
      application/
        port/in/
        port/out/
        service/
      adapter/
        in/web/
        in/scheduler/
        out/platform/
          telegram/
          facebook/
          x/
        out/persistence/

    operations/
      adapter/in/web/
      application/service/

    monetization/              # optional, isolated product capability
      domain/
      application/
      adapter/

  platform/
    configuration/
    persistence/
    scheduling/
    http/
    browser/
    security/
    observability/
~~~

Package names may be adapted to idiomatic Java naming, but ownership and dependency rules are mandatory.

### 4.1 Module API rule

A module exposes only a small public application API:

- masterdata: canonical identity resolution and master-data query use cases.
- match: fixture and match-statistics query/projection use cases.
- ingestion: discovery, synchronization and replay use cases.
- analytics: readiness and analysis use cases.
- content: draft creation and approval use cases.
- publication: publication submission and delivery status use cases.

Do not make every service/repository public. Keep internal classes package-private where practical.

## 5. Current code disposition

This is the initial disposition based on the inspected source tree and key classes. Every REMOVE action requires a code-reference, configuration, database and runtime consumer check.

### 5.1 KEEP — preserve capability and core design

| Existing area | Decision | Reason |
|---|---|---|
| Provider DTOs and provider-specific mappers | KEEP | Isolate source schemas |
| Provider-specific clients and browser session management | KEEP | Necessary transport boundary; harden lifecycle and limits |
| Provider capability ports and registry | KEEP | Different providers support different capabilities |
| DataSourcePolicy and ProviderRoute | KEEP + REFACTOR | Correct source-routing ownership; improve canonical coverage metadata |
| IngestionWorkUnit and IngestionPlan | KEEP | Correct unit of provider/dataset/league/season execution |
| ProviderSyncCheckpoint | KEEP + REFACTOR | Resource-level resumability; clarify retry ownership |
| IngestionDistributedLock | KEEP + HARDEN | Cross-instance exclusion; add fencing/connection safety tests |
| Workflow run and stage execution records | KEEP + REFACTOR | End-to-end state is separate from provider work-unit state |
| External-ID mapping entities | KEEP | Canonical/provider identity separation |
| Match field provenance and statistics source snapshots | KEEP + EXTEND | Existing source traceability |
| Canonical League, Season, Team, Player and Match concepts | KEEP + REFACTOR | Valid domain concepts; decouple JPA representation incrementally |
| Analytics feature/model/backtest concepts | KEEP + REFACTOR | Core product value; enforce model/data-readiness contracts |
| Content draft/version/claim provenance concepts | KEEP | Needed for auditable content |
| Publication state machine and attempt records | KEEP + REFACTOR | Correct lifecycle boundary |
| Quartz as current scheduler | KEEP | Already integrated and suitable for current monolith |

### 5.2 REFACTOR — required target improvements

| Existing area | Required change |
|---|---|
| com.mmo.core | Split reusable platform infrastructure from obsolete generic business utilities |
| domain JPA entities | Introduce domain models and persistence entities behind repository adapters incrementally |
| ingest application services | Each use case owns one operation; remove duplicated orchestration and shared mutable support logic |
| ingest orchestration | Keep deterministic work-unit execution; extract explicit policies and result reporting |
| workflow | Keep only cross-module sequencing and run/stage state; delegate work to module use cases |
| DataSourcePolicy | Replace display-name assumptions with canonical competition coverage metadata |
| Provider adapters | Return neutral provider models and typed outcomes; no canonical JPA writes or source ownership decisions |
| Persistence adapters | Own transactions, batch operations, uniqueness and upsert semantics |
| Controllers | Thin validation and use-case invocation; no long synchronous provider work |
| Quartz jobs | Thin triggers only; no duplicated business orchestration |
| Analytics services | Separate feature calculation, model execution, evaluation and readiness |
| Content generation | Consume persisted, versioned analytics results; enforce claim provenance and approval |
| Publication | One platform-neutral lifecycle; platform adapters implement delivery protocol |
| Configuration | Typed, validated configuration per provider/module; no secrets in source or logs |
| Error handling | Stable domain/application error codes and centralized API error mapping |
| Tests | Build unit, adapter contract, MySQL integration and workflow reliability suites |

### 5.3 REPLACE — replace after parity is demonstrated

1. Replace the manual/unversioned application schema evolution process with a version-controlled migration system. Flyway is the preferred default. First baseline the actual production schema; never let a migration tool recreate or drop existing data.
2. Replace generic dynamic conversion/import in football ingestion with typed provider DTO -> neutral model -> validated canonical projection. Retain generic file import only if another active module demonstrably needs it.
3. Replace direct JPA entity exposure and generic Spring Data REST endpoints with explicit application query APIs and DTOs.
4. Replace ad-hoc provider response handling with a common typed result contract for success, empty, unsupported, restricted, partial and retryable/permanent failure.
5. Replace scattered diagnostics with one secured ingestion operations API and structured execution reports.

### 5.4 REMOVE — planned removal candidates

These are not deleted in this documentation PR. They are candidates for implementation PRs after dependency and production usage checks.

| Candidate | Removal condition |
|---|---|
| Generic product/CSV/Excel import classes in com.mmo.core.importer | Remove from football application if no active product module uses them; replace historical football imports with typed adapters |
| Generic dynamic conversion classes | Remove after all consumers move to explicit mappers |
| Spring Data REST starter/configuration | Remove after confirming no repository REST endpoints are consumed externally |
| Unused Spring Batch configuration/jobs | Remove if active ingestion uses the work-unit executor and no other module needs Batch |
| Duplicate service interfaces/implementations with no real port boundary | Collapse into one use case or a genuine port |
| Duplicate/obsolete controllers and old diagnostics | Remove after endpoint usage audit and API migration |
| Dead provider adapters and configuration keys | Remove only when capability is explicitly retired |
| Copied/obsolete templates and old implementation guides | Archive/delete after canonical documentation is established |
| Unused dependencies, including legacy social/payment libraries | Remove after dependency and runtime usage analysis |
| Test/demo classes packaged in main source | Move to test source or delete after checking runtime references |

### 5.5 Out-of-scope legacy feature removal

The current source tree contains a third-party market-quote/bookmaker feature family. It is not part of the approved football data science product scope and must not be expanded.

Plan to remove this feature family end-to-end after verifying data consumers and backup/retention requirements:

- Provider adapters and persistence adapters dedicated to market quotes.
- Domain entities, repositories, history tables and enrichment states dedicated only to that feature.
- Schedulers, endpoints, configuration, DTOs, templates and analytics references dedicated only to that feature.
- Related enums, labels, logs, generated content and obsolete documentation.

Do not delete tables or historical records in the first cleanup PR. First stop new writes, remove readers and API exposure, archive/export data if required, then drop schema in a separate reviewed migration.

Legacy model labels and content templates that do not meet neutral data-science terminology must also be renamed or removed through a controlled compatibility migration.

### 5.6 Monetization disposition

Subscription, payment and conversion code is not part of football ingestion or analytics core. It is not automatically deleted because it may represent a real product capability.

- Keep it isolated as an optional module.
- No analytics, ingestion or content domain entity may depend on monetization entities.
- If subscriptions/payments are not part of the product, remove the module in a separate decision and data-retention plan.
- Monetization must not shape canonical football data or analytics models.

## 6. Canonical data architecture

### 6.1 Master data

Canonical master data owns league, season, team, player, reviewed aliases and provider identity mappings.

Existing external-ID tables are retained unless the identity audit demonstrates a concrete schema defect. Do not add a generic mapping table that duplicates current entity-specific mappings.

Provider IDs are opaque strings. Canonical IDs are MMO-owned. Names and slugs are attributes, not identity keys.

### 6.2 Match data

Match context owns fixture identity and schedule, match status and result, home/away relationship, lineups and participation, match/team/player observations, provenance and source freshness.

Identity resolution must handle kickoff changes, provider home/away orientation and ambiguous team aliases. Never auto-merge entities on display name alone.

### 6.3 Source observation and raw retention

The current match-statistics source snapshot is not a universal raw data archive.

Introduce generalized append-only source observations only after defining:

- provider, capability, entity type and external ID;
- fetched timestamp and source-updated timestamp;
- payload hash, schema/normalizer version and correlation ID;
- payload or immutable object-storage reference;
- parse/projection status and error code;
- retention, compression, size limits and replay eligibility.

Raw observations must not contain API keys, authorization headers, cookies, browser profiles or unnecessary personal information. Reprocessing must not mutate the original payload.

### 6.4 Canonical projection and source policy

~~~text
Provider response
  -> typed provider DTO
  -> neutral provider model
  -> raw source observation
  -> identity resolution
  -> validation and normalization
  -> source policy / conflict resolution
  -> canonical persistence + provenance
  -> readiness evaluation
~~~

Source ownership is field- and dataset-specific, not one global provider priority. Preserve conflicting observations; do not overwrite curated canonical values with blank or invalid source values.

## 7. Ingestion execution architecture

There are three distinct levels:

1. Workflow run/stage — end-to-end process across modules.
2. Ingestion work unit — one provider + dataset + league + season execution.
3. Provider checkpoint — cursor/progress for that work unit.

Use existing workflow run/stage entities for level 1 and existing work-unit/checkpoint model for levels 2–3. A separate campaign/task queue is prohibited unless a measured requirement cannot be met and an architecture decision record approves it.

Required behavior:

- Deterministic work-unit identity and idempotent writes.
- Explicit primary and fallback provider roles.
- Bounded provider-specific concurrency and rate limits.
- Retryable vs permanent failure classification.
- Attempt history and last-error visibility.
- Lease/token fencing and safe stale-run recovery.
- Independent retry/replay by provider, dataset, league and season.
- No network/browser operation inside a database transaction.
- Checkpoint completion only after persistence succeeds.
- Historical backfill and active-season refresh use separate checkpoint semantics.
- Structured execution report with counts, durations and failure codes.

Quartz is only a trigger. It invokes application use cases and owns no business logic.

## 8. Analytics architecture

Analytics is downstream from canonical data and readiness.

Recommended separation:

- feature: immutable, versioned model input construction.
- model: Poisson, Dixon-Coles, form/EMA, team-strength and other statistical models.
- evaluation: walk-forward validation, calibration, error metrics and model comparison.
- readiness: dataset coverage, freshness and blocker reasons.
- analysis: persisted match/team/player analysis outputs.

Every model documents input definitions, units, estimation window, missing-data policy, version, calibration and evaluation. A model result is an estimate, not an observed fact.

No analytics service may call provider HTTP clients or scrape source websites.

## 9. Content and publication architecture

Content generation consumes persisted, versioned analysis artifacts.

~~~text
Ready canonical data
  -> versioned analytics output
  -> content draft
  -> claim/provenance validation
  -> editorial approval
  -> publication request
  -> platform delivery attempt
  -> delivery reconciliation
~~~

- Each numeric claim points to a canonical dataset and analysis/model version.
- Draft generation must not bypass readiness.
- Approval is distinct from generation.
- Publication adapters own only platform protocol and delivery response handling.
- Unknown delivery outcomes are reconciled before retry.
- Do not publish directly from ingestion or analytics controllers.

## 10. Configuration, API and security standards

- Use typed @ConfigurationProperties with validation for provider and module configuration.
- Keep credentials in environment/secret stores; never log them.
- Disable public repository/entity exposure.
- Secure discovery, replay, rebuild and administrative endpoints with explicit roles and audit records.
- Long-running ingestion is submitted as a durable execution request; HTTP handlers do not wait for an entire crawl.
- Use stable response/error DTOs and centralized exception mapping.
- Apply timeouts, bounded retries and provider rate limits.
- Use UTC for execution timestamps and preserve source timezone/date meaning for competition schedules.
- Add correlation IDs to logs and execution reports.

## 11. Build and dependency standard

- Keep Java 17 until a separately approved runtime upgrade.
- Keep Spring MVC as the HTTP server. Use WebClient where useful; do not mix blocking Playwright/JPA work into reactive event loops.
- Keep Playwright for browser-only provider acquisition; isolate browser lifecycle and resource limits.
- Keep Quartz for scheduling in the current deployment model.
- Keep Commons Math only where statistical implementations use it.
- Keep Thymeleaf only if server-rendered views remain supported.
- Keep social SDKs inside publication adapters.
- Remove unused libraries only after source and runtime usage scans.
- Prefer Spring Boot dependency management; avoid unnecessary explicit versions for managed dependencies.
- Add static architecture checks (ArchUnit or equivalent) to prevent module dependency violations.

Flyway should be introduced in a dedicated database-governance PR after a baseline of the real existing schema has been captured and verified. Do not combine migration-system adoption with broad table redesign.

## 12. Testing standard

The current 12 Java test files are insufficient for a 505-file application. Test investment is a prerequisite for safe removal/refactoring.

Required layers:

1. Domain unit tests for invariants and identity rules.
2. Application service tests with JUnit 5/Mockito.
3. Provider adapter contract tests using recorded fixtures and mocked HTTP/browser boundaries.
4. Persistence integration tests against MySQL for unique constraints, transactions, upserts, locks and checkpoint behavior.
5. Workflow tests for lease renewal, fencing, restart, retry and partial failure.
6. Analytics tests for deterministic output, boundary conditions, missing data and evaluation.
7. Content/publication tests for provenance, approval, duplicate prevention and delivery reconciliation.
8. Architecture tests for forbidden module dependencies.

H2 may be used for fast tests, but it is not proof of MySQL-specific behavior. MySQL integration tests are mandatory for database locks, isolation, SQL dialect, indexes and migration behavior.

Minimum CI command:

~~~bash
./mvnw -B -ntp clean verify
~~~

The repository must include a Maven wrapper or document the required Maven version and CI setup.

## 13. Database migration and data safety

Before changing schema:

1. Capture the actual deployed schema and table sizes.
2. Verify a restorable backup.
3. Establish a schema baseline for the migration tool.
4. Add forward-only versioned migrations and a tested recovery procedure.
5. Test migration on a production-like copy.
6. Deploy additive changes first.
7. Backfill in bounded, restartable batches.
8. Compare row counts, checksums, identity coverage and application behavior.
9. Switch readers/writers only after parity.
10. Drop obsolete columns/tables in a later migration after the rollback window.

No ordinary PR may truncate or reset a live database. No entity rename or table deletion without consumer audit and explicit data-retention approval.

## 14. Ordered implementation roadmap

This order is mandatory. Do not start broad source moves or removals before Phase 0.

### Phase 0 — Full inventory and safety baseline

- Generate package/class dependency graph.
- Enumerate all Spring beans, controllers, scheduled jobs, configuration keys and provider capabilities.
- Map every repository/entity/table and its consumers.
- Identify duplicate services and unreachable classes.
- Record all external API consumers and endpoint usage.
- Capture production schema and backup/restore evidence.
- Establish Maven wrapper/CI and MySQL integration-test environment.
- Create an initial KEEP/REFACTOR/REPLACE/REMOVE inventory with exact class-level evidence.

Exit: every planned removal has verified zero-consumer evidence or a migration path; baseline tests/build are reproducible.

### Phase 1 — Platform foundation and architecture enforcement

- Create shared platform configuration, error handling, security and observability boundaries.
- Introduce module API rules and architecture tests.
- Remove accidental cross-module repository/entity access.
- Introduce versioned database migration governance after schema baseline.
- Create root README and canonical documentation index.

Exit: module dependency rules are executable in CI; production schema evolution is reproducible.

### Phase 2 — Canonical master data and match boundary

- Separate canonical domain concepts from JPA persistence representations incrementally.
- Centralize identity resolution and reviewed aliases.
- Normalize provider external IDs and unique constraints.
- Centralize match projection, result and statistics provenance.
- Remove duplicate persistence logic after parity.

Exit: idempotent canonical identity and match writes; no name-only auto-merge.

### Phase 3 — Provider and ingestion refactor

- Normalize provider capability and typed outcome contracts.
- Refactor provider adapters to DTO -> neutral model, without canonical JPA writes.
- Consolidate source routing and provider coverage metadata.
- Harden work-unit/checkpoint/lock execution.
- Add independent retry/replay and operations APIs.
- Add append-only raw observations where retention requirements justify them.

Exit: every provider dataset is independently observable, replayable and idempotent.

### Phase 4 — Analytics refactor

- Separate feature construction, model execution, evaluation and readiness.
- Version model inputs and outputs.
- Remove duplicate/obsolete analytics services and endpoints.
- Add MySQL-backed data coverage and model evaluation reports.

Exit: analytics only runs on ready canonical scopes and is reproducible by model version.

### Phase 5 — Content and publication refactor

- Unify content draft/approval lifecycle.
- Enforce claim-level provenance and numeric validation.
- Standardize platform adapter and delivery-attempt behavior.
- Remove duplicate publisher paths and obsolete templates.

Exit: all published analysis claims are traceable and delivery retries are safe.

### Phase 6 — Legacy removal

- Remove unused generic importer/converter and framework dependencies.
- Remove the out-of-scope market-quote feature family after data retirement approval.
- Remove unused endpoints, configuration, tables, templates and libraries.
- Remove compatibility adapters only after usage reaches zero.

Exit: no dead code, orphaned tables or undocumented runtime schedules remain.

### Phase 7 — Performance and scale

- Profile database query plans and ingestion throughput.
- Tune bounded batch sizes, indexes, connection pool and provider concurrency.
- Consider external queue or service separation only with measured evidence.
- Add retention/archive automation and operational SLOs.

Exit: performance targets and recovery objectives are measured, not assumed.

## 15. Rules for implementation PRs

Every implementation PR must:

1. Reference a phase and requirement in this document.
2. List existing classes reused, refactored, replaced or removed.
3. Include dependency/consumer evidence for every deletion.
4. State whether database schema changes.
5. Include migration, backfill, rollback and data-retention plans where needed.
6. Include JUnit 5/Mockito tests for application logic.
7. Include MySQL integration tests for database-sensitive behavior.
8. Include provider fixture/contract tests when source adapters change.
9. Report exact build/test commands and results; never claim tests ran when they did not.
10. Describe operational impact: schedules, rate limits, secrets, endpoints, logs and metrics.
11. Avoid unrelated refactors and keep one coherent business change per PR.
12. End with a Conventional Commit message.

## 16. Non-negotiable constraints

- No big-bang rewrite.
- No second canonical database model.
- No parallel orchestration framework without approved gap analysis.
- No direct JPA access across module boundaries.
- No provider adapter writes to canonical entities.
- No controller or Quartz business logic.
- No network/browser operation inside long database transactions.
- No name-only entity merging.
- No silent deletion or destructive database reset.
- No credentials or personal data in logs/raw observations.
- No gambling-related terminology or concepts in source code, variable names, logs, APIs, generated content or user-facing documentation. Use neutral data-science language: expected goals, observed metrics, model estimates, probability distributions, source provenance and data quality.

## 17. Architecture acceptance checklist

The target architecture is established only when:

- [ ] Module dependency rules are enforced by automated tests.
- [ ] Each bounded context has a documented owner and public application API.
- [ ] Canonical domain logic is independent of provider and persistence adapters.
- [ ] Provider acquisition returns typed neutral models and explicit outcomes.
- [ ] Identity and field-level provenance are deterministic and auditable.
- [ ] Ingestion work units and workflow stages have non-overlapping responsibilities.
- [ ] Retry, replay, lock and lease behavior is integration-tested on MySQL.
- [ ] Analytics inputs/outputs are versioned and gated by readiness.
- [ ] Content claims are traceable to persisted analytics outputs.
- [ ] Publication attempts are idempotent and reconciled.
- [ ] Database migrations are version-controlled and production-safe.
- [ ] Planned legacy removals have zero-consumer evidence and data-retention approval.
- [ ] CI runs clean verification and architecture tests.
