# Football Ingest Package Architecture

## Package layout

```text
com.mmo.module.fb.ingest
├── application
│   ├── bootstrap
│   │   └── FootballDataBootstrapUseCase
│   ├── sync
│   │   └── FootballDataSyncUseCase
│   ├── enrichment
│   │   └── MatchEnrichmentUseCase
│   ├── resource
│   │   ├── LeagueIngestionServiceImpl
│   │   ├── SeasonIngestionServiceImpl
│   │   ├── TeamIngestionServiceImpl
│   │   ├── FixtureIngestionServiceImpl
│   │   ├── StandingsIngestionServiceImpl
│   │   └── OddsIngestionServiceImpl
│   ├── support
│   │   └── IngestionServiceSupport
│   ├── lock
│   │   └── IngestionDistributedLock
│   └── port
│       └── out
│           ├── FootballDataUkIngestionPort
│           ├── HistoricalStatisticsIngestionPort
│           ├── MatchStatisticsIngestionPort
│           ├── PlayerStatisticsIngestionPort
│           └── ProviderCheckpointStore
├── provider
│   ├── <provider>/client
│   ├── <provider>/dto
│   ├── <provider>/mapper
│   ├── <provider>/*Provider
│   ├── model                 # provider-neutral transport models
│   └── spi                   # provider capabilities and registry
├── persistence
│   ├── ProviderCheckpointJpaAdapter
│   ├── LeaguePersistenceServiceImpl
│   ├── TeamPersistenceServiceImpl
│   ├── FixturePersistenceServiceImpl
│   ├── StandingsPersistenceServiceImpl
│   └── OddsPersistenceServiceImpl
├── entity                    # ingest operational entities
├── repository                # Spring Data persistence contracts
├── job                       # Quartz adapters only
└── service                   # inbound use-case/resource contracts
```

The application use cases are deliberately separated from provider and persistence implementations. The public service interfaces in `service` remain the inbound contracts used by the existing Quartz adapters and API callers during this incremental migration.

## Dependency rules

1. `job` may call only inbound use-case interfaces. Quartz jobs do not select provider adapters, parse provider DTOs or write entities.
2. `application` owns workflow sequencing, provider selection, retry/checkpoint decisions and cross-resource orchestration.
3. `application` depends on service contracts and `application.port.out`; it must not depend on provider implementation classes or Spring Data repositories/entities.
4. Provider adapters under `provider` own HTTP/browser transport, provider DTOs and provider-specific mapping into provider-neutral ingestion models.
5. Persistence adapters implement output ports and own JPA entities/repositories. Canonical football entities remain in the league/team/match/player modules.
6. Provider external IDs are provider-scoped. Standardization produces MMO canonical identity; it must not rewrite another provider's external ID.
7. Core fixture identity/kickoff/status/result and match enrichment (statistics/xG/player metrics) are separate workflows.

## Workflow ownership

- Bootstrap: historical Core import and recovery only. Team and fixture completion markers are independent and keyed by provider/resource. Football-Data UK has its own historical Core marker.
- Core Sync: active-season Core refresh, standings, upcoming odds and Football-Data UK current-season Core refresh.
- Match Enrichment: historical/current match statistics, xG and player statistics. It never creates canonical Core fixtures.
- A stage writes its completion marker only after the entire stage reports success. Partial per-season progress remains in the provider-specific importer checkpoint mechanism.

## Distributed execution lock

All three top-level use cases acquire the same MySQL advisory lock, `mmo:fb:ingest:global`, scoped with the active database catalog and a zero-second wait. This avoids contention between dev/test schemas sharing one MySQL server:

- Lock contention skips the current execution and logs a warning; it does not queue a stale run.
- The lock is held on one dedicated JDBC connection for the entire use case. Repository work continues through normal Spring-managed connections/transactions.
- The lock is released in `finally`; closing the owning connection is the fallback release mechanism.
- MySQL advisory locks coordinate only processes connected to the same MySQL primary/server. Every MMO instance must use that same writer endpoint.
- Do not annotate these top-level use cases with a long-running `@Transactional`; that would unnecessarily hold a second database connection and transaction across network calls.

## Test strategy

- Unit tests cover lock acquired/busy/error/release paths.
- Use-case tests cover bootstrap checkpoint isolation, retry boundaries, no-op after completion, lock contention, and Core/Enrichment stage selection.
- Provider adapter tests cover provider DTO mapping, HTTP retry/classification and CSV parsing independently.
- GitHub Actions starts a MySQL 8 service and runs the two-connection advisory-lock integration test. Local runs can enable the same test with MMO_MYSQL_TEST_URL, MMO_MYSQL_TEST_USERNAME and MMO_MYSQL_TEST_PASSWORD. The first connection must acquire the lock, the second must fail, then must succeed after the first connection releases/closes.
- Run `mvn clean verify` and inspect Quartz/job logs before enabling the recurring schedules.

## Remaining migration boundary

The resource-level Core ingestion use cases now live under `application.resource`; their JPA persistence implementations live under `persistence`. Provider-specific CSV/Understat/SofaScore import workflows remain under the legacy `service.impl` package for a later adapter extraction. Migrate those workflows incrementally behind the output ports, with contract tests per provider. Do not perform a blind package rename: preserve transaction boundaries, provider registry selection, external-ID ownership, and existing upsert semantics.
