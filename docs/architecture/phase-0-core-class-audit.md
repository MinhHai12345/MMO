# Phase 0 — Core Class Audit

> Repository: `MinhHai12345/MMO`  
> Branch baseline: `main`  
> Baseline commit inspected: `f49b4771f9a564c82b2352286c7b3dec6d857cb7`  
> Audit date: 2026-11-18  
> Scope: all Java source files currently under `src/main/java/com/mmo/core`.

## 1. Executive result

The current `com.mmo.core` package contains **44 Java source files**. It is not a coherent business core: it combines a legacy generic importer, a dynamic mapper, a database-backed Quartz job registry, global REST response transformation, JPA base classes, bootstrap initialization and a small number of technical configurations.

The target architecture remains **Modular Monolith + Layered Architecture**. The target does not contain a catch-all `core` package.

This audit is a **class inventory and responsibility assessment**. It is not yet a complete consumer/reference proof: GitHub's code-search integration did not return usable references for the queried symbols, and a repository-wide local grep/build was not available in this execution environment. Therefore no class is marked safe to delete, and no source files or database objects are changed by this PR.

## 2. Counts by responsibility

| Responsibility | Files | Initial assessment |
|---|---:|---|
| Configuration | 2 | Split by technical ownership |
| Generic converter | 4 | Replace with typed module-owned mappers where active |
| Legacy cron-job subsystem | 11 | Compare with current workflow + Quartz usage before retirement |
| JPA base entities | 2 | Move only after inheritance and entity-scan audit |
| Global REST response handler | 1 | Preserve API contract; replace with explicit API response/error handling |
| Generic importer | 15 | Likely legacy/product-oriented; verify routes, jobs, data and consumers |
| Startup initialization | 2 | Move active football initialization to owning module/bootstrap |
| Response models | 3 | Keep only if shared API contract; otherwise module-owned |
| Utilities | 2 | Move only if genuinely cross-module |
| **Total** | **44** | |

## 3. Class-level disposition

Legend:
- **KEEP / MOVE** — responsibility is valid; move to its owning target package after reference checks.
- **REFACTOR** — retain behavior but redesign ownership or contract.
- **REPLACE** — migrate consumers to a clearer implementation before retiring the old type.
- **VERIFY** — insufficient evidence to decide; do not move/delete yet.

### 3.1 Configuration (2)

| Current class | Proposed target | Disposition | Risk / required check |
|---|---|---|---|
| `core.configuration.SpringDataRestConfiguration` | `infrastructure.web` temporarily; likely remove Spring Data REST later | VERIFY / REPLACE | Find exported repositories and clients relying on generated endpoints, pagination parameter names, media types and exposed IDs before removing starter/configuration. |
| `core.configuration.TelegramConfiguration` | `infrastructure.telegram` or `module.publication.channel.telegram` | KEEP / MOVE | Confirm TelegramClient injection points and token/property binding; keep bean name and startup behavior. |

### 3.2 Generic converter (4)

| Current class | Proposed target | Disposition | Risk / required check |
|---|---|---|---|
| `core.converter.AbstractMapper` | Owning module's `mapper` package | VERIFY / REPLACE | Find subclasses and Spring component registration. |
| `core.converter.DynamicConverter` | No global target | REPLACE | Find all injection points and conversion pairs; replace with explicit typed mapper methods. |
| `core.converter.Mapper` | Owning module's `mapper` package | VERIFY / REPLACE | Find implementations and generic type assumptions. |
| `core.converter.impl.DynamicConverterImpl` | No global target | REPLACE | Inspect all runtime mapper beans and reflective construction behavior before removal. |

### 3.3 Legacy cron-job subsystem (11)

| Current class | Proposed target | Disposition | Risk / required check |
|---|---|---|---|
| `core.cronjob.controller.CronJobController` | `module.operations.controller` temporarily | REFACTOR | Preserve `/cronjobs` routes until API consumers are identified; inspect authorization and response shape. |
| `core.cronjob.entity.CronJob` | `module.operations.domain` or remove after migration | VERIFY | JPA table, persisted rows, Quartz job class names and startup recovery depend on this record. |
| `core.cronjob.entity.JobCompleteState` | Owning scheduler/workflow model | VERIFY | Find enum references and persisted string/ordinal values. |
| `core.cronjob.entity.JobState` | Owning scheduler/workflow model | VERIFY | Find enum references, API serialization and database values. |
| `core.cronjob.exception.InvalidCronJobException` | `shared.exception` or operations | VERIFY | Find throw/catch sites and API mapping. |
| `core.cronjob.exception.ScheduleJobException` | `module.operations` or `shared.exception` | VERIFY | Find throw/catch sites and API mapping. |
| `core.cronjob.job.AbstractJob` | Owning module's scheduler adapter | VERIFY | Find subclasses and persisted fully qualified class names. |
| `core.cronjob.job.CronJobFactory` | `infrastructure.scheduling` or remove | VERIFY | Inspect Quartz registration and Spring bean lookup. |
| `core.cronjob.job.JobScheduleCreator` | `infrastructure.scheduling` | KEEP / MOVE | Check trigger identity, job identity, misfire policy and scheduler-store compatibility. |
| `core.cronjob.listener.JobStateListener` | `infrastructure.scheduling` or workflow | VERIFY | Inspect listener registration, persisted state transitions and job keys. |
| `core.cronjob.repository.CronJobRepository` | Same owner as CronJob or remove after migration | VERIFY | Must not delete while entity/table or runtime service still uses it. |
| `core.cronjob.service.CronJobService` | `module.operations.service` temporarily | REFACTOR | Check all callers, including controllers, jobs and startup registration. |
| `core.cronjob.service.impl.CronJobServiceImpl` | `module.operations.service` temporarily | REFACTOR | Current implementation dynamically loads job classes by persisted class name and triggers Quartz jobs; preserve recovery and state semantics until migration is proven. |

### 3.4 JPA base entities (2)

| Current class | Proposed target | Disposition | Risk / required check |
|---|---|---|---|
| `core.entity.AbstractAuditingEntity` | `shared.persistence` only if truly shared; otherwise module persistence base | VERIFY / MOVE | Find all subclasses, column mappings, auditing configuration and schema expectations. |
| `core.entity.AbstractEntity` | `shared.persistence` only if truly shared; otherwise module persistence base | VERIFY / MOVE | Find all subclasses and inherited ID/equals/hashCode behavior. |

### 3.5 REST response handling (1)

| Current class | Proposed target | Disposition | Risk / required check |
|---|---|---|---|
| `core.handler.GlobalResponseHandler` | `shared.web` temporarily; replace with explicit API DTOs and exception advice | REFACTOR | It wraps every response body, including null and arbitrary DTOs. Inspect every controller contract, file response, Page and HATEOAS response before changing behavior. |

### 3.6 Generic importer (15)

| Current class | Proposed target | Disposition | Risk / required check |
|---|---|---|---|
| `core.importer.DynamicImportJobConfig` | Remove only after verifying Spring Batch jobs are unused | VERIFY / REMOVE CANDIDATE | Inspect job/step beans, job repository tables, configuration and scheduled launches. |
| `core.importer.controller.ImportController` | Owning module API or remove after API usage check | VERIFY | Check route consumers, upload formats and authorization. |
| `core.importer.model.ImportContext` | Owning import module or remove | VERIFY | Find all strategy and reader/writer consumers. |
| `core.importer.model.product.ProductCsvModel` | Remove candidate / isolated legacy product area | VERIFY / REMOVE CANDIDATE | Confirm no remaining product-domain feature or persisted import use. |
| `core.importer.processor.DynamicItemProcessor` | No global target | VERIFY / REMOVE CANDIDATE | Find Spring Batch step references and runtime job configuration. |
| `core.importer.reader.CsvItemReaderFactory` | No global target | VERIFY / REMOVE CANDIDATE | Find consumers and supported CSV contracts. |
| `core.importer.reader.DynamicItemReaderFactory` | No global target | VERIFY / REMOVE CANDIDATE | Find consumers and reflection/configuration references. |
| `core.importer.reader.ExcelItemReaderFactory` | No global target | VERIFY / REMOVE CANDIDATE | Find consumers, templates and uploaded file dependencies. |
| `core.importer.reader.ItemReaderFactory` | No global target | VERIFY / REMOVE CANDIDATE | Find implementations and injection points. |
| `core.importer.reader.JsonItemReaderFactory` | No global target | VERIFY / REMOVE CANDIDATE | Find consumers and input schema contracts. |
| `core.importer.strategy.ImportStrategy` | Owning module if still active | VERIFY | Find implementations and registry usage. |
| `core.importer.strategy.ImportStrategyRegistry` | Owning module if still active | VERIFY | Find consumers and strategy registration behavior. |
| `core.importer.strategy.impl.ProductImportStrategy` | Remove candidate / isolated legacy product area | VERIFY / REMOVE CANDIDATE | Confirm product tables, API and data retention dependencies before retirement. |
| `core.importer.util.ExcelUtils` | Owning module utility or remove | VERIFY | Find call sites and supported file formats. |
| `core.importer.writer.DynamicItemWriter` | No global target | VERIFY / REMOVE CANDIDATE | Find Spring Batch step references and write targets. |

### 3.7 Startup initialization (2)

| Current class | Proposed target | Disposition | Risk / required check |
|---|---|---|---|
| `core.initialize.DataInitializationRunner` | `bootstrap.initialization` | REFACTOR / MOVE | Check startup event ordering, auto-import flag and all registered initializers. |
| `core.initialize.DataInitializer` | Bootstrap contract or owning module | VERIFY / MOVE | Find implementations and ensure startup work is idempotent and bounded. |

### 3.8 API response models (3)

| Current class | Proposed target | Disposition | Risk / required check |
|---|---|---|---|
| `core.model.response.Error` | `shared.web.dto` or replace | VERIFY | Find response serialization and API contract consumers. |
| `core.model.response.Pageable` | `shared.web.dto` or replace with Spring Page metadata DTO | VERIFY | Find API clients and page numbering semantics. |
| `core.model.response.Response` | `shared.web.dto` temporarily; replace only with versioned API migration | VERIFY / REFACTOR | GlobalResponseHandler and controllers may depend on exact JSON envelope. |

### 3.9 Utilities (2)

| Current class | Proposed target | Disposition | Risk / required check |
|---|---|---|---|
| `core.utils.BeanUtils` | `shared.util` only if cross-module | VERIFY | Find usages; compare with Spring BeanUtils/MapStruct and avoid duplicate helpers. |
| `core.utils.DateTimeUtils` | `shared.time` only if cross-module | VERIFY | Find usages and timezone/locale assumptions; prefer java.time types and explicit zones. |

## 4. Important findings from inspected implementations

1. **CronJob is not just a generic scheduler wrapper.** `CronJobServiceImpl` persists job definitions and status, dynamically loads a class name from the database, manages Quartz JobDetail/Trigger state, and supports pause/resume/stop/manual trigger. It must not be deleted or replaced by workflow Quartz jobs without checking persisted rows and active job definitions.
2. **GlobalResponseHandler changes every MVC response.** It wraps arbitrary response objects, Spring `Page`, and HATEOAS models. Removing it can silently change every endpoint's JSON contract.
3. **Spring Data REST is globally configured.** The configuration exposes IDs for every managed JPA entity and customizes pagination/media settings. Before removing Spring Data REST, inventory repository interfaces and generated endpoints.
4. **The generic converter uses runtime type matching.** Removing it requires identifying every mapper bean and each source/target conversion pair; typed replacements should be explicit and module-owned.
5. **The importer appears generic and product-oriented.** Its Spring Batch wiring and product CSV strategy are candidates for retirement, but job metadata tables, upload endpoints, and product data consumers must be checked first.
6. **Startup initialization is a runtime behavior.** The runner executes all registered `DataInitializer` implementations when `initialData.autoImport` is enabled. Preserve ordering and idempotency when moving it.

## 5. Consumer proof required before source changes

The following checks are mandatory and remain outstanding:

- Repository-wide search for all 44 fully qualified class names, imports, inheritance and constructor injection references.
- Search for string-based references: class names in database rows, Quartz JobDataMap, configuration, reflection, Jackson type metadata and Spring bean names.
- Inventory all `@RepositoryRestResource` / Spring Data repositories exposed by Spring Data REST.
- Inventory `@RequestMapping` routes for `/cronjobs` and importer endpoints, including API documentation and external callers.
- Inspect Quartz tables and application cron-job registry rows in the deployed database; record active job keys, groups, class names and triggers.
- Inspect Spring Batch metadata tables and any persisted job/step definitions.
- Inspect entity inheritance and generated SQL/schema mappings for both base entities.
- Inspect every `DataInitializer` implementation and `initialData.autoImport` environment configuration.
- Run `./mvnw -DskipTests compile`, then `./mvnw test`, then application-context and MySQL integration tests in CI or a connected development environment.

## 6. Recommended implementation batches after this audit

1. **Batch A — Safe technical ownership moves:** Telegram configuration, shared response/error handling only after API contract tests, and truly cross-module utilities. Keep bean names and routes stable.
2. **Batch B — Scheduler convergence decision:** compare legacy `CronJob` registry with existing workflow Quartz jobs. Choose one scheduling ownership model; migrate active definitions and persisted state before retiring legacy code.
3. **Batch C — Importer retirement:** verify there are no live import endpoints/jobs or required product data consumers; then remove the obsolete importer and only then evaluate Spring Batch dependency removal.
4. **Batch D — Persistence base classes:** move abstract JPA classes only after all subclasses and entity scanning are mapped.
5. **Batch E — Typed mapping:** replace global dynamic converter with explicit mappers in the owning modules, with mapping tests.
6. **Batch F — Remove `com.mmo.core`:** only after all 44 files have been moved or retired and repository-wide search confirms no package references.

## 7. Refactoring progress on this branch

- [x] Move `TelegramConfiguration` from `com.mmo.core.configuration` to `com.mmo.infrastructure.telegram`; preserve the `telegramClient` bean name and token property binding.
- [ ] Move global response models/handler after API contract inventory.
- [ ] Move JPA base classes after subclass/reference inventory.
- [ ] Converge legacy CronJob ownership with workflow scheduling after persisted job inventory.
- [ ] Replace dynamic converter with typed module-owned mappers after consumer mapping.
- [ ] Retire generic importer only after endpoint, Spring Batch and data-consumer checks.

## 8. Scope and safety

- This document adds no source-code, API, Quartz, schema or data changes.
- No class is approved for deletion by this audit alone.
- No Flyway baseline or schema migration is introduced in this phase.
- Maven build/tests were not run in this environment.
- This document must be updated with consumer-search evidence and CI results before source-moving PRs are approved.
