# MMO Repository Restructuring Inventory — Baseline

> Inspected branch: main
> Inspected commit: a7e2a5be38591d5318745d5c7e928b76bf6e34b5
> Inventory date: 2026-11-18
> Purpose: package-level baseline for the Modular Monolith + Layered Architecture restructuring.

## 1. Repository snapshot

| Metric | Observed |
|---|---:|
| Tracked files | 553 |
| Java files under src/main/java | 505 |
| Java files under src/test/java | 12 |
| Main Java packages | 121 |
| Main source files under com.mmo.core | 50 |
| Main source files under com.mmo.module.fb | 453 |
| Main source files directly under com.mmo | 2 |

The 12 Java test files are all under the ingestion area. This is a test distribution observation, not a claim that all application behavior is untested by other means.

## 2. Current package distribution

### com.mmo.core — 50 main-source Java files

| Current package | Files | Initial target |
|---|---:|---|
| configuration | 8 | Split between bootstrap and infrastructure by responsibility |
| converter / converter.impl | 4 | Typed mapper in owning module; remove generic converter after consumer audit |
| cronjob/* | 11 | Quartz infrastructure + workflow/owning-module job adapters |
| entity | 2 | Owning module or shared only if truly generic |
| handler | 1 | shared/exception or API error handling |
| importer/* | 15 | Active owning module or controlled retirement after consumer audit |
| initialize | 2 | bootstrap or owning module |
| model/response | 3 | shared/response only if cross-module and stable |
| utils | 2 | shared/util only if genuinely cross-module |

The counts above are based on current source package paths. Exact class-level moves require reference, bean, reflection, configuration and runtime-consumer analysis.

### com.mmo.module.fb — 453 main-source Java files

| Current package family | Files | Initial target |
|---|---:|---|
| admin | 1 | operations, after endpoint responsibility review |
| analytics | 56 | analytics |
| content | 35 | content |
| domain | 56 | Split canonical reference data to masterdata and match-owned entities to match |
| ingest | 244 | ingestion; split provider adapters, application orchestration and persistence clearly |
| initial | 1 | bootstrap or owning module after startup behavior review |
| monetization | 20 | monetization, isolated and optional |
| publish | 26 | publication; rename only after import and API impact review |
| workflow | 14 | workflow |

## 3. Existing package-level observations

1. The repository already has meaningful business areas; the restructuring should clarify ownership rather than replace all code.
2. The largest concentration is ingestion (244 files), so it must be refactored by provider capability and use case, not moved as one undifferentiated package.
3. The current domain package combines League/Season/Team/Player and Match concepts. The target separates masterdata and match ownership.
4. The current core package mixes application configuration, Quartz, generic converters, import utilities, response models and technical setup. It must be decomposed by responsibility.
5. Workflow already exists and should be retained as the coordinator of multi-stage execution. Do not introduce another orchestration engine.
6. The current publish package already contains platform-specific code and lifecycle behavior; preserve its behavior while establishing publication ownership.
7. Test files currently concentrate on ingestion and master-data initialization/reconciliation. New tests are required before broad movement or deletion.

## 4. Immediate class-level audit required

Before source moves, the implementation must map:

- All Java imports and fully-qualified references to classes being moved.
- Spring component scanning, bean names, conditional configuration and reflection.
- REST routes, request/response contracts and external consumers.
- Quartz job classes, job keys, triggers, listeners and persisted scheduler state.
- Configuration property names and environment variables.
- JPA entity scans, repository scans, entity relationships and table mappings.
- SQL/native queries, JPQL, Thymeleaf references and serialization behavior.
- Provider registry entries, capabilities, endpoint specifications and scheduled workflows.
- Runtime dependencies and data consumers for legacy importer/monetization/other optional areas.

## 5. Safety rules for first code PRs

- Preserve database table names, column names, entity mappings and identifiers during package-only moves.
- Do not rename API routes or Quartz job keys in the same PR as package moves.
- Do not remove generic importer/converter code until all consumers are found and migrated.
- Do not remove Spring Data REST, Spring Batch or dependencies based only on source-tree appearance.
- Do not move every class in `com.mmo.core` to `shared`; most are not shared business primitives.
- Run the Maven verification suite and MySQL integration tests in a local/CI environment before merging source changes.
- If build/test execution is not available, keep the change documentation-only and state that limitation.

## 6. First implementation batches

1. Build exact dependency/consumer map for `com.mmo.core` and identify active versus legacy components.
2. Establish target module package boundaries without moving domain code yet.
3. Move low-risk technical configuration in isolated PRs, preserving bean names and behavior.
4. Split domain ownership into masterdata and match using package-only moves first.
5. Restructure ingestion by provider and application responsibility.
6. Rename publish to publication only after API, configuration and import impact is known.
7. Remove `com.mmo.core` only when empty and verified.

This inventory is a package-level baseline. It is not a claim that all 505 production classes have already undergone line-by-line consumer analysis.


## 7. Progress on refactor/modular-layered-architecture

The first low-risk configuration move has been applied on the restructuring branch:

| Original class | New package |
|---|---|
| `AppConfiguration` | `com.mmo.bootstrap.configuration` |
| `JpaAuditingConfiguration` | `com.mmo.infrastructure.persistence` |
| `PlaywrightConfiguration` | `com.mmo.infrastructure.browser` |
| `CronJobConfiguration` | `com.mmo.infrastructure.scheduling` |
| `TaskConfiguration` | `com.mmo.infrastructure.scheduling` |
| `ThymeleafConfig` | `com.mmo.infrastructure.web` |

The restructuring branch now contains 44 main-source Java files under `com.mmo.core` (down from the 50-file main baseline). The remaining 44 files are not classified as safe to move or delete yet. In particular, the legacy importer, generic converters, cron-job persistence/services, response types and utilities need class-level consumer analysis.

This first move preserves configuration bean methods and bean names. The package remains within the `com.mmo` component-scan root. A Maven build has not been run in this environment; CI/local verification is still required before merge.
