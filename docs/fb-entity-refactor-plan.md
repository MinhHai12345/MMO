# FB Entity Refactor Plan

## Scope and rollout

The FB module is in active implementation. The database schema will be dropped and recreated, so this refactor intentionally targets a clean schema rather than preserving legacy columns or backfilling old records. Hibernate-generated schema is not a substitute for validating the entity model and repository queries.

## Canonical provider identity

- Internal entity primary keys remain canonical IDs used by application relationships.
- Provider IDs live only in `LeagueExternalId`, `SeasonExternalId`, `TeamExternalId`, `MatchExternalId`, and `PlayerExternalId` mapping tables.
- Identity is always scoped by `Provider + externalId`; no core entity stores a provider-agnostic `externalId`.
- A given internal entity has at most one mapping per provider, and a provider external ID maps to at most one internal entity.
- `Season.year` is the human/domain season label, never a provider ID.
- Registration must be idempotent and must reject collisions rather than silently reassign an external ID.

## Core football entities

- `League`: internal identity, stable slug, display metadata, one canonical `isActive` activation flag, and popularity attributes.
- `Season`: belongs to a league; unique by `league_id + year`; carries current/start/end metadata and provider mappings.
- `Team`: internal team identity and descriptive metadata; provider IDs are separate mappings.
- `Match`: belongs to a season and home/away teams; `matchTime` is the sole kickoff field, `roundInfo` is the sole round field, status/score fields are canonical, and provider IDs are separate mappings.
- `LeagueStanding`: derives league identity through `Season` rather than duplicating `league_id`, and preserves `TOTAL`, `HOME`, and `AWAY` as separate rows. Unknown provider standing types must not overwrite `TOTAL`.
- `MatchAdvancedStats` and `PlayerMatchStats`: uniqueness reflects one record per match/team or match/player respectively.
- `MatchOdds`: a current odds snapshot is unique by match, bookmaker and market. Market identity must include the line/handicap where the provider distinguishes multiple lines.
- `OddsHistory`: retains historical observations with a recorded timestamp and indexes for chronological lookup.
- `MatchPrediction`: currently uses a shared match identity. Actual match result is stored only in `PredictionPerformance`, which is one-to-one with a prediction. Before implementing prediction versioning/backtesting, migrate it to an independently identified prediction run with model version and calculation timestamp, and update all consumers together.

## Entity integrity rules

- Prefer explicit database column names, nullability, enum string lengths, decimal precision/scale, named unique constraints, and query-aligned indexes.
- Use lazy fetching for to-one relationships unless the use case proves eager loading is needed.
- Avoid redundant unique declarations on both a column and table constraint.
- Do not change Java numeric types without updating all mappers, analytics, DTOs, and tests in the same change.
- Avoid cascade delete semantics on core history records unless deletion is explicitly part of the domain.
- Validate that `LeagueStanding.league` matches `Season.league` when writing standings.
- Preserve terminal match status against stale provider updates; do not create a new match without a known kickoff unless the domain explicitly supports TBD fixtures.

## Verification checklist

- Search all main and test sources for references to removed legacy identity fields.
- Verify provider identity uniqueness and collision behavior in `ExternalIdMappingServiceTest`.
- Verify `syncCoreData()` order: leagues/seasons, teams, fixtures, then dependent resources.
- Add tests for standings type/group mapping, match status transitions, odds snapshot identity, and prediction versioning before implementing those changes.
- Run `./mvnw test` and inspect Hibernate DDL in a clean database before merging.
