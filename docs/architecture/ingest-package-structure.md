# Ingest package architecture

The ingestion module uses a modular hexagonal architecture. Dependencies point inward:
`adapter -> application -> domain`. Application code must not depend on scheduler, web, JPA implementation, or provider-specific DTO/client classes.

## Canonical package layout

```text
com.mmo.module.fb.ingest
├── adapter
│   ├── in
│   │   ├── scheduler                 # Quartz jobs; thin invocation adapters
│   │   └── web                       # REST controllers; request/response mapping only
│   └── out
│       └── persistence
│           ├── core                  # canonical league/team/match/odds/standing persistence
│           └── provider
│               ├── footballdatauk    # provider-specific statistics persistence
│               └── sofascore         # provider-specific player statistics persistence
├── application
│   ├── exception                     # application-level partial/failure semantics
│   ├── lock                          # ingestion workflow lock abstraction/use
│   ├── model                         # commands, work units and result models
│   ├── orchestration                 # plan, backfill, sync and enrichment coordination
│   ├── port
│   │   ├── in                        # use cases called by jobs/controllers/other modules
│   │   └── out                       # persistence/provider/checkpoint capabilities
│   └── service
│       ├── identity                  # canonical external-ID mapping use case
│       ├── ingestion                 # league/season/team/fixture/standings/odds workflows
│       ├── integration               # provider enrichment and bootstrap application services
│       └── support                   # shared application workflow support
├── domain
│   ├── model                         # provider/dataset/checkpoint concepts
│   └── policy                        # source ownership and routing policy
└── provider                          # transitional provider adapter package; migration pending
```

## Dependency rules

1. `adapter.in.scheduler` and `adapter.in.web` depend only on `application.port.in` and application request/result models. They do not call concrete application services.
2. `application.orchestration` coordinates work units and depends on input/output ports. It contains no HTTP, JPA, Quartz or provider DTO logic.
3. `application.service` implements use cases and coordinates provider capabilities with persistence ports. Provider routing is policy-driven, not hard-coded into jobs.
4. `application.port.out` defines the capabilities required by the application. Implementations belong under `adapter.out`.
5. `adapter.out.persistence` owns transaction boundaries, JPA repositories, batch prefetch/upsert, source-aware merge and provider snapshot persistence.
6. Provider clients, DTOs and mappers must be isolated from canonical domain entities. They normalize into provider-neutral ingestion models before application persistence.
7. No application service may call a JPA repository directly unless that repository is deliberately exposed as an application output port. Persistence implementation details remain in adapters.
8. Cross-provider identity resolution is performed against canonical MMO IDs and provider external-ID mappings. Provider IDs must never leak into canonical identity fields.

## Refactor completed in this change

- Added canonical `application.port.in` contracts for ingestion, backfill, sync and enrichment use cases.
- Added canonical `application.port.out` contracts for persistence capabilities.
- Kept the old `ingest.service` interfaces as deprecated compatibility facades extending the new ports. New code must not import them.
- Moved resource ingestion implementations to `application.service.ingestion`.
- Moved provider bootstrap/enrichment integration services to `application.service.integration`.
- Moved identity mapping orchestration to `application.service.identity`.
- Moved provider override, resolved fixture and SofaScore mapping result records to `application.model`.
- Moved Quartz jobs to `adapter.in.scheduler` and the mapping REST controller to `adapter.in.web`.
- Moved persistence implementations to `adapter.out.persistence`, separated core persistence from provider-specific enrichment persistence.
- Updated persistence tests to match the adapter package boundaries.
- Made the web adapter depend on `SofaScoreExternalMappingUseCase`, not its concrete implementation.

## Transitional packages that still need migration

The provider implementation tree is still under `ingest.provider`; it should move to `adapter.out.provider` in a separate provider-adapter migration. The shared provider-neutral records/SPI should be extracted to application output-port/model packages before moving provider-specific clients and DTOs.

The source-routing policy and provider/checkpoint enums still live under `ingest.policy` and `ingest.enums`. They are domain concepts and should move to `ingest.domain.policy` and `ingest.domain.model` in a follow-up that updates all consumers together.

Checkpoint/health entities and repositories still live in the legacy `ingest.entity` and `ingest.repository` packages; move them to the persistence monitoring adapter together with their migrations and integration tests.

Deprecated `ingest.service` model/interface facades should be removed only after downstream callers have migrated. Do not reintroduce dependencies on these facades.
