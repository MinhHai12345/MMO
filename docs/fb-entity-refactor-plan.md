# FB Entity Refactor Plan

## Goal

Harden the football domain model without breaking existing ingestion, analytics, or stored data. Changes that affect persisted schema must be staged and include a migration/backfill strategy.

## Phase 1 — Provider identity normalization

- Treat `MatchExternalId`, `LeagueExternalId`, `SeasonExternalId`, and `TeamExternalId` as the canonical provider identity mappings.
- Inventory all reads/writes of legacy `externalId` fields and migrate callers to provider-aware lookup.
- Backfill mapping rows from existing legacy IDs only when the source provider is known; never guess a provider.
- Detect collisions before enforcing constraints or removing legacy columns.
- Remove legacy fields only in a later schema change after callers and data are migrated.

## Phase 2 — Core match and season integrity

- Define the canonical meaning of `matchTime` and `startTimestamp`; preserve unknown kickoff times if a provider reports TBD.
- Normalize provider statuses into an internal lifecycle and prevent stale sync responses from regressing terminal match states.
- Validate home/away teams and score values.
- Define a consistent uniqueness policy for a season within a league, and check existing duplicate rows before adding a constraint.
- Remove duplicated league/season relationships only after all queries and ingestion code have been audited.

## Phase 3 — Standings integrity

- Preserve `StandingType.TOTAL`, `HOME`, and `AWAY` as separate rows.
- Keep the uniqueness key aligned with the domain: season + team + standing type, adding group scope only if provider data proves it is needed.
- Validate that a standing's league matches its season's league.
- Do not silently convert an unknown provider standing type to `TOTAL`; log or reject unknown values so bad data cannot overwrite the overall table.
- Add migration checks for existing duplicate or inconsistent rows.

## Phase 4 — Prediction versioning

- Replace the shared-primary-key one-prediction-per-match assumption with an independently identified prediction record.
- Include model version and prediction/calculation timestamp, and define a deterministic key for a prediction run.
- Preserve historical predictions for walk-forward backtesting and calibration.
- Separate prediction outputs from publication-specific fields (Telegram/ Facebook/ X) in a later compatible step.
- Define probability and confidence units explicitly and validate probability ranges/sums.

## Phase 5 — Odds and history

- Define whether odds rows represent a current snapshot or a historical observation.
- Normalize market identity, including line/handicap where relevant, before adding uniqueness constraints.
- Ensure history captures the fields needed for audit/backtesting and has a non-null observation timestamp.
- Keep odds numeric values as `BigDecimal`.

## Verification checklist

- Inventory entity references, repositories, mappers, ingestion services, analytics, and database initialization behavior.
- Inspect existing database shape and duplicate/orphan data before writing destructive migrations.
- Add tests for provider-ID collisions, standing-type mapping, status transitions, prediction versioning, and odds snapshot identity.
- Run the Maven test/build suite and inspect generated schema changes before merge.

## Rollout policy

The application currently configures Hibernate schema auto-update in its default configuration. Do not rely on that alone for destructive changes. Introduce explicit, ordered migrations and deploy additive/backfill steps before removing legacy columns or changing primary-key semantics.
