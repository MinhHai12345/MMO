# Match domain model

## Responsibilities

`fb.match` owns the canonical fixture and match-related persistence model. It does not own
provider HTTP clients or provider DTOs. Ingestion adapters map provider payloads into the
canonical match model and persist provider-specific provenance separately.

## Entity boundaries

### Canonical fixture

- `Match` is the canonical fixture: season, home/away teams, kickoff, venue/referee,
  score, result, status and the analytics-facing fixture xG projection.
- `MatchExternalId` maps one canonical match to one external ID per provider. Both
  `(match_id, provider)` and `(provider, external_id)` are unique.
- A match cannot reference the same team as both home and away. Optimistic versioning
  protects concurrent updates to the canonical fixture.

### Enrichment execution state

`MatchEnrichmentState` stores the lifecycle of one provider/resource pair:

`(match_id, provider, resource)`

Resources are independent: `TEAM_STATISTICS`, `PLAYER_STATISTICS` and `ODDS`.
Statuses distinguish pending, in-progress, succeeded, empty, skipped and failed outcomes.
Retry time and last-attempt time belong to this row, not to the canonical Match columns.

The former `last_odds_synced_at`, `statistics_last_attempt_at` and
`player_statistics_last_attempt_at` fields have been removed from the Match entity and all
current readers/writers. Existing physical columns are left untouched by Hibernate
`ddl-auto: update`; a later explicit migration can drop them after database verification.
Current odds/team-statistics/player-statistics candidate queries use provider/resource-specific state.

### Statistics

- `MatchAdvancedStats` remains the merged, analytics-facing projection, unique by
  `(match_id, team_id)`.
- `MatchStatisticsSourceSnapshot` stores the latest raw JSON payload independently for
  each `(match_id, team_id, provider)`. This preserves provenance when Football-Data UK,
  SofaScore and Understat contribute different metrics to the same canonical projection.
- Legacy provider/timestamp fields on `MatchAdvancedStats` remain readable during
  compatibility migration; new ingestion paths write provenance to source snapshots.

### Odds

- `MatchOddsQuote` is the normalized quote model. One row represents a single outcome,
  provider, bookmaker, market type, period and line.
- A zero line represents markets without a line so MySQL unique-key semantics remain
  deterministic. Lines are normalized to scale 3; odds must be positive.
- `OddsHistory` can reference either the legacy grouped `MatchOdds` row or a normalized
  quote. Quote history records the outcome, line, price and observation timestamp.
- `MatchOdds` remains as a compatibility projection for existing API/report consumers.
  Current API-Football and Football-Data UK ingestion writes both the legacy projection
  and normalized quotes.

Market types include match result, over/under, both-teams-to-score, Asian handicap,
double chance and other. Periods distinguish opening, closing, current pre-match and live.

### Head-to-head

`H2HRecord` is intentionally directional. The A→B and B→A rows are separate because
wins and goals are expressed from the first team's perspective. The advanced metrics job
rebuilds both directions from finished canonical matches.

## Repository boundaries

- `MatchRepository`: canonical fixture reads/writes and bounded match candidate queries.
- `MatchExternalIdRepository`: provider identity lookup.
- `MatchAdvancedStatsRepository`: merged team-in-match statistics.
- `MatchStatisticsSourceSnapshotRepository`: source provenance snapshots.
- `MatchEnrichmentStateRepository`: provider/resource retry state.
- `MatchOddsRepository`: legacy grouped odds projection.
- `MatchOddsQuoteRepository`: normalized odds quotes.
- `OddsHistoryRepository`: immutable price history.
- `H2HRecordRepository`: directional head-to-head aggregates.
- `BookmakerRepository`: bookmaker and market aggregate identities.

## Schema compatibility

The application currently uses Hibernate `ddl-auto: update`. This change is additive:
new state/snapshot/quote tables and optimistic-lock version columns are introduced; existing
columns and legacy odds/statistics rows are retained. No existing match, odds or statistics
rows are deleted or rewritten by schema initialization.

For production environments that disable Hibernate schema updates, apply the equivalent
DDL for the new tables and columns before deploying this version. Legacy columns should only
be removed in a later migration after all old readers and writers have been retired.
