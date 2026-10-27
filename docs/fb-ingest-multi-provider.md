# Multi-provider ingestion

## Contract

- `FootballDataBootstrapService.syncCoreDataForProviders(List<Provider>)` runs core ingestion in dependency order: leagues/seasons for all providers, teams for all providers, then fixtures for all providers.
- `FOOTBALL_DATA` runs first and is the canonical writer for core league/season/team/match fields.
- Other providers attach their external IDs to uniquely matched canonical entities. They do not create unmatched entities or overwrite canonical fields.
- Provider IDs remain namespaced by provider through the existing external-ID mapping tables and `ExternalIdMappingService`.
- The method de-duplicates the supplied providers and rejects an empty list.

## Data that remains single-source

Standings are currently keyed by season/team/type and have no provider dimension, so the multi-provider method persists standings only from `FOOTBALL_DATA`. Odds are also kept out of this core bootstrap because current odds persistence does not record the source provider and would merge providers into the same bookmaker/market rows. To store standings/odds from multiple providers independently, their persistence schema needs a provider/source dimension and migration first.

## Example

Call the service with `List.of(Provider.FOOTBALL_DATA, Provider.SOFA_SCORE)`. This first establishes canonical entities and then attaches SofaScore IDs. No new scheduler is introduced; callers can use the existing bootstrap/job entry point.
