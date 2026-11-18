# MMO Target Architecture and Restructuring Standard

> Status: Approved target direction for incremental implementation
> Architecture: Modular Monolith + Layered Architecture
> Runtime baseline: Java 17, Spring Boot 3.4.x, MySQL
> Scope: Restructure the existing MMO repository; preserve working behavior and production data.

## 1. Architecture decision

MMO will use a **Modular Monolith organized by business capability, with Layered Architecture inside each module**.

The project will not adopt Microservices or require full Hexagonal/Clean Architecture. Ports and adapters may be used selectively where they solve a real integration or provider-substitution problem, but they are not a mandatory package pattern.

The application remains one deployable Spring Boot application and one primary MySQL database. Modules have explicit ownership and communicate through public application services/contracts.

The goal is not to maximize architectural patterns. The goal is clear ownership, predictable dependencies, testability, reliable ingestion and safe evolution of the existing system.

## 2. Target top-level structure

```text
com.mmo
├── Application.java
├── bootstrap/
│   └── configuration/
├── shared/
│   ├── exception/
│   ├── response/
│   ├── validation/
│   ├── time/
│   └── util/
├── infrastructure/
│   ├── persistence/
│   ├── http/
│   ├── browser/
│   ├── scheduling/
│   ├── security/
│   └── observability/
└── module/
    ├── masterdata/
    ├── match/
    ├── ingestion/
    ├── workflow/
    ├── analytics/
    ├── content/
    ├── publication/
    ├── operations/
    └── monetization/       # optional, isolated until product decision
```

Do not create empty package trees in advance. Add a package when a real class is moved or created.

## 3. Layering inside a business module

Use only the layers needed by that module. A typical module is:

```text
module/<name>/
├── api/                    # REST controllers and request/response DTOs
├── application/            # use cases, orchestration, transaction boundaries
├── domain/                 # business entities, value objects and rules
├── repository/             # persistence access used by application services
└── infrastructure/         # provider/client-specific or technical implementations
```

Responsibilities:

| Layer | Owns | Must not do |
|---|---|---|
| API / Presentation | HTTP contract, validation, request mapping | Business decisions, direct repository orchestration |
| Application | Use-case flow, transactions, coordination, authorization decisions | Browser/API parsing, presentation concerns |
| Domain | Business invariants, identity rules, calculations intrinsic to the domain | Spring MVC, provider DTOs, external HTTP |
| Repository | Queries and persistence operations | Cross-module business orchestration |
| Infrastructure | HTTP/browser clients, JPA-specific implementation, technical configuration | Own business policy |

A module may omit `api` if it has no external entry point, and omit `infrastructure` if it has no dedicated technical adapter.

### 3.1 Repository convention

Repository access belongs to the module that owns the data. Application services may use that module's repository interfaces.

A module must not call another module's repository or manipulate another module's JPA entities. It must call the owning module's public application service or consume a stable DTO/contract.

Do not introduce an interface for every class. Add an interface only when there is a real boundary, multiple implementations, a test seam that materially helps, or an external integration contract.

### 3.2 Entity and persistence convention

Do not perform a big-bang separation of every JPA entity from the domain model. Preserve current mappings while restructuring package ownership. Extract persistence models only when a concrete persistence/domain coupling causes a defect or blocks a needed change.

Never expose JPA entities directly from REST endpoints or across module boundaries.

## 4. Business module ownership

### 4.1 masterdata

Owns canonical identity and reference data:
- League, Season, Team, Player, Country, Venue where present.
- Canonical identifiers, aliases and identity resolution.
- Provider-to-canonical identity mappings.

Provider-specific master-data payloads remain in ingestion. Canonical master-data rules and persistence belong here.

### 4.2 match

Owns canonical match lifecycle and observations:
- Fixture identity, kickoff, participants and status.
- Result, lineups, events and match-level statistics.
- Advanced observations such as xG, PPDA and related provenance.
- Source freshness and field-level provenance where match-owned.

Keep observed source data distinct from calculated analytics outputs.

### 4.3 ingestion

Owns source acquisition and projection:
- Provider registry/capabilities and provider routing.
- SofaScore, Understat, football-data and other provider clients.
- Provider DTOs, parsing, typed mappers and neutral provider models.
- Ingestion work units, sync checkpoints, retry classification and source execution reports.
- Requests to masterdata/match application services for canonical persistence.

Provider adapters must not write directly to canonical JPA repositories. No provider DTO may leak into analytics, content or public API contracts.

### 4.4 workflow

Owns end-to-end execution coordination:
- Workflow run/stage state.
- Scheduling triggers and stage ordering.
- Retry/recovery coordination, leases and execution status.

Quartz is a trigger, not the business workflow engine. Ingestion owns provider work-unit/checkpoint semantics; workflow owns cross-stage execution. Do not introduce a second orchestration framework.

### 4.5 analytics

Owns reproducible statistical analysis:
- Feature construction and versioning.
- Form/EMA, Poisson and other statistical models.
- xG/PPDA analysis and Match Attractiveness Index.
- Readiness, evaluation, calibration and backtesting.
- Versioned analysis outputs with input references.

Analytics must consume canonical data and must not call provider clients or scrape websites. A model estimate must remain distinguishable from an observed fact.

### 4.6 content

Owns editorial artifacts:
- Analysis context assembly from persisted data.
- Prompt templates and LLM integration.
- Drafts, versions, quality checks and claim provenance.
- Video scripts and other content formats.

Content must not recalculate analytics or make unsupported numerical claims.

### 4.7 publication

Owns delivery to external platforms:
- Telegram, Facebook and future platform adapters.
- Publication requests, attempts, retries, external IDs and delivery reconciliation.

Publication must not generate content or own analytics logic. Rename the current `publish` package only after import/reference impact is mapped.

### 4.8 operations

Owns operational APIs and diagnostics:
- Secured sync status and execution reports.
- Administrative actions such as replay/rebuild where authorized.
- Health and operational visibility.

Business controllers stay in their owning modules. Operations must not become a second business service layer.

### 4.9 monetization

Keep isolated as optional product functionality until product scope is decided. Football data, ingestion, analytics and content must not depend on monetization entities or services.

## 5. Shared and infrastructure boundaries

### shared

Only small, stable, genuinely cross-module primitives belong here: common error types, response envelope (if consistently used), validation helpers, clock/time abstractions and simple utilities.

Do not put business entities, repositories, provider DTOs, module services or generic "manager" classes in shared. Shared must not become a renamed core dumping ground.

### infrastructure

Owns technical integrations used by the application: database configuration, HTTP/WebClient configuration, Playwright lifecycle, Quartz infrastructure, security, metrics and logging.

A technical component used by one module should remain inside that module unless it is demonstrably shared.

## 6. What happens to the current com.mmo.core

The target architecture has **no top-level core package**. The current `com.mmo.core` is transitional and will be emptied through controlled moves:

| Current responsibility | Target |
|---|---|
| Application-wide configuration | `bootstrap/configuration` or the owning infrastructure package |
| JPA auditing and persistence configuration | `infrastructure/persistence` |
| Playwright lifecycle/configuration | `infrastructure/browser` or ingestion infrastructure |
| HTTP/WebClient configuration | `infrastructure/http` or ingestion infrastructure |
| Quartz configuration and generic scheduling | `infrastructure/scheduling`; actual jobs belong to workflow/owning module |
| Generic cron-job domain/services | Workflow or operations only after consumer/state audit |
| Generic exception handler | `shared/exception` |
| Common response DTO | `shared/response` only if genuinely shared |
| Generic converter/mapper | Replace with explicit typed mappers in the owning module; remove after consumer audit |
| Product/CSV/Excel importer | Isolate in an active owning module or retire after consumer/data audit |
| Generic utility | `shared/util` only if stable and used by multiple modules |

Do not move all 50 classes mechanically. Each class must be assigned by responsibility and its consumers, bean registrations, reflection use, configuration keys, scheduled triggers and persistence impact must be checked.

The `core` package is removed only when it contains no live classes and the application still starts and passes its verification suite.

## 7. Dependency rules

1. Presentation -> Application -> Domain.
2. Application may use repositories owned by its own module.
3. Infrastructure implements technical access for its owning module.
4. Cross-module calls use public application services/contracts, never internal repositories or JPA entities.
5. Domain must not depend on controllers, schedulers, provider DTOs, HTTP clients or external SDKs.
6. Ingestion may call masterdata/match application contracts; masterdata/match must not depend on ingestion.
7. Analytics depends on canonical masterdata/match contracts, never ingestion.
8. Content consumes persisted analytics outputs; it does not call model calculators as an alternate analytics pipeline.
9. Publication consumes approved content artifacts; it does not call analytics or provider clients.
10. Shared and infrastructure must not depend on business modules in a way that creates reverse dependencies.

## 8. End-to-end data flow

```text
Quartz / REST / Admin
        |
        v
Workflow application (when multi-stage coordination is needed)
        |
        v
Ingestion application
        |
        v
Provider client -> Provider DTO -> Typed mapper
        |
        v
Identity resolution + validation + source policy
        |
        v
Masterdata / Match application services
        |
        v
Canonical MySQL data + provenance
        |
        v
Analytics features -> Models -> Evaluation / Readiness
        |
        v
Persisted analysis output
        |
        v
Content draft -> Validation / Approval
        |
        v
Publication request -> Platform delivery attempt
```

Not every sync requires a multi-stage workflow. Simple use cases should call the ingestion application service directly rather than introducing orchestration layers without need.

## 9. Data and reliability standards

- Canonical IDs are MMO-owned; provider IDs are external identifiers.
- Sync operations are deterministic and idempotent.
- Source conflict resolution is field/dataset-specific, not a single global provider priority.
- Preserve source provenance and distinguish source observations from model estimates.
- Never merge entities on display name alone.
- Do not run network/browser operations inside long database transactions.
- Use bounded concurrency, provider-specific rate limits and classified retry behavior.
- Checkpoint completion occurs only after the corresponding persistence succeeds.
- Transactions are short and scoped to a work unit or persistence operation.
- Keep UTC for execution timestamps and preserve competition-local schedule meaning.
- No destructive production schema change without a verified backup, migration and data-retention plan.

## 10. Testing and code quality

- JUnit 5/Mockito unit tests for application services and important domain rules.
- Provider contract tests using fixtures for parsing and mapping.
- MySQL integration tests for unique constraints, upserts, transaction behavior, locking and migrations.
- Workflow tests for retry, partial failure, lease expiry and recovery.
- Analytics tests for deterministic results, missing inputs and boundary conditions.
- Content/publication tests for provenance, duplicate prevention and delivery reconciliation.
- Architecture tests to prevent forbidden cross-module dependencies.
- CI must run `./mvnw -B -ntp clean verify`; report only commands that were actually executed.

Keep Java 17 and the current Spring Boot baseline during the first restructuring passes. Framework/runtime upgrades are separate changes after compatibility and deployment checks; do not mix package migration with a major platform upgrade.

## 11. Restructuring sequence

### Phase 0 — Inventory and safety baseline
- Inventory all production Java classes, packages, Spring beans, controllers, Quartz jobs, configuration properties, repositories, entities and database tables.
- Map class references and cross-module dependencies.
- Map endpoints and external consumers.
- Identify active workflows and scheduled jobs.
- Establish reproducible build/test and production schema/backup evidence.
- Produce exact KEEP / REFACTOR / REPLACE / REMOVE decisions with consumer evidence.

Exit: every planned move/removal has a known target and impact list; baseline build/test is reproducible.

### Phase 1 — Foundation
- Apply package naming and module boundary rules.
- Move low-risk shared/infrastructure classes in small batches.
- Add architecture tests and centralized API error handling.
- Remove direct cross-module repository access.
- Add or update project README and module documentation.

### Phase 2 — Masterdata and Match
- Move canonical League/Season/Team/Player ownership to masterdata.
- Move fixture/result/statistics ownership to match.
- Preserve table names and JPA mappings initially.
- Consolidate identity resolution and persistence behavior with parity tests.

### Phase 3 — Ingestion and Workflow
- Organize provider-specific clients/DTOs/mappers under ingestion.
- Standardize provider capabilities and typed outcomes.
- Preserve and harden work units, checkpoints, locks and workflow stages.
- Move operational diagnostics to operations.

### Phase 4 — Analytics
- Consolidate features, models, readiness and evaluation.
- Version inputs/outputs and preserve reproducibility.

### Phase 5 — Content and Publication
- Separate content generation and editorial lifecycle from delivery.
- Standardize platform adapters and safe delivery retry.

### Phase 6 — Legacy removal
- Remove dead generic importer/converter code, unused frameworks and dependencies only after zero-consumer evidence.
- Retire out-of-scope feature families only after data-retention approval.
- Remove `com.mmo.core` after all live responsibilities have moved.

### Phase 7 — Runtime and performance improvements
- Profile database/query/ingestion behavior.
- Optimize measured bottlenecks.
- Consider queues or service extraction only with operational evidence.

## 12. Implementation PR requirements

Every restructuring PR must:
1. Reference a phase and this document.
2. List exact classes moved, changed, replaced or removed.
3. Include consumer/reference evidence for moves and removals.
4. Preserve database table names and mappings unless a separately reviewed migration is included.
5. Include tests for changed behavior and package boundaries.
6. State exact build/test commands and results; never imply an unrun build passed.
7. Describe impact on API routes, Quartz jobs, configuration keys, secrets and runtime operations.
8. Avoid unrelated business changes.
9. End with a Conventional Commit message.

## 13. Non-negotiable constraints

- No big-bang rewrite.
- No Microservices migration in this phase.
- No mandatory full Hexagonal Architecture.
- No top-level `core` package in the target structure.
- No second canonical data model.
- No provider adapter direct writes to canonical persistence.
- No cross-module repository/entity access.
- No business logic in controllers or Quartz triggers.
- No network/browser work inside long transactions.
- No destructive database reset or silent data deletion.
- No credentials in logs or source.
- No gambling-related terminology or concepts in code, logs, APIs, generated content or documentation. Use neutral data-science terminology.

## 14. Acceptance criteria

The restructuring is complete when:
- [ ] Every business capability has one owning module.
- [ ] Top-level `com.mmo.core` no longer exists.
- [ ] Shared contains only stable cross-module primitives.
- [ ] Infrastructure contains technical concerns, not business workflows.
- [ ] Cross-module dependencies follow the rules in section 7.
- [ ] Provider DTOs do not leak beyond ingestion.
- [ ] Canonical data and analytics outputs remain distinct and traceable.
- [ ] Existing API and scheduled behavior is either preserved or explicitly migrated.
- [ ] Database schema/data are preserved through the refactor.
- [ ] Build, tests and architecture checks pass in CI.
