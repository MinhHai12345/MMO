# FB Provider Architecture

## Dependency direction

`Quartz Job -> Ingestion Application Service -> Resource Ingestion Service -> ProviderRegistry -> Provider SPI -> Provider Adapter -> Provider Client / DTO / Mapper`

Persistence is a separate boundary:

`Provider-neutral Model -> Persistence Service -> MMO Domain Repository`

A provider adapter must never depend on MMO JPA entities, repositories, or another provider's DTOs.

## Provider module responsibilities

Each provider package owns:

- HTTP/browser/CSV transport client and provider-specific retry/error classification.
- Provider DTOs (transport-only records).
- Provider mapper converting transport DTOs to `ingest.provider.model` records.
- Provider adapter implementing one or more typed SPI contracts.

The shared `ProviderRegistry` indexes implementations by `Provider` and resource contract. A provider may implement only the resources it actually supports; missing capabilities fail explicitly during resolution.

## Resource contracts

- `LeagueProvider`
- `SeasonProvider`
- `TeamProvider`
- `FixtureProvider`
- `StandingsProvider`
- `OddsProvider`
- `HistoricalStatsProvider`

Provider implementations must return provider-neutral records. Resource ingestion services resolve canonical league/team/season mappings and delegate persistence to persistence services.

## Placement rules

- `UnderStatSyncService` is an application ingestion service: it coordinates Understat data with canonical MMO matches and persists enrichment. Understat HTTP/DTO/mapper code stays in `provider.understats`.
- `SofaScoreMatchStatisticsIngestionService` is an application ingestion service because it selects canonical matches, resolves external IDs and persists match statistics. Browser client and transport DTOs stay in `provider.sofascore`.
- `SofaScoreBootstrapServiceImpl` is an application bootstrap use case and remains outside the provider package.
- `FootballDataUkSyncService` is an application import use case. CSV download and parsing stay in `provider.footballdatauk`; persistence adapters that write MMO entities live in `ingest.persistence.footballdatauk`.
- Provider clients accept provider IDs and provider-neutral scalar/query values. They must not accept MMO domain entities or query MMO repositories.

## Canonicalization

Provider-specific mappers normalize source names and IDs into source-neutral provider records. Shared cross-provider aliases and canonicalization rules are centralized in `MmoProviderDataStandardizer`; provider adapters must not embed cross-provider name matching in application services.

## Error handling

Transport failures, rate limits, authentication failures, restricted-plan responses, CAPTCHA/challenge responses and invalid provider payloads remain distinguishable. Retry policy belongs to the provider transport boundary; application services decide whether a resource failure is fatal, retryable, or skippable.
