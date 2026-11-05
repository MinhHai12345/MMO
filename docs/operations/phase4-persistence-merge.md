# Phase 4 — Persistence and Merge

## Canonical identity
- Match identity remains the canonical MMO match ID.
- Provider IDs are unique by `(provider, external_id)` and `(match_id, provider)`.
- Cross-provider fixture matching is allowed only for one unambiguous candidate in the same season, same home/away orientation and kickoff tolerance window. Ambiguous candidates are rejected, never guessed.
- Team and season IDs are resolved before persistence; provider IDs are not stored in canonical domain columns.

## Source-aware field merge
`SourceAwareMergeService` applies the dataset's `ProviderRoute` at field granularity:
- The primary provider can refresh fields it owns.
- A secondary provider can fill a null field or refresh a field whose provenance already belongs to that same provider.
- A secondary provider cannot overwrite a non-null field owned by another provider or legacy data without provenance.
- Older source revisions cannot replace newer revisions from the same provider.
- Each accepted canonical field update records provider, dataset, source update time and fetch time in `fb_match_field_provenance`.
- Fixture fields, team-in-match metrics (Football-Data UK and SofaScore), match-level xG and player-in-match metrics (SofaScore) use the same provenance gate. Player metric keys are scoped by canonical player ID within the match.

Provider adapters that do not expose a source revision timestamp currently persist `source_updated_at = null`; `fetched_at` remains the ingestion observation time. Do not treat fetch time as an upstream source revision.

Do not derive points or scores from partial source data. Null remains unknown; zero is a real value.

## Batch persistence
Each fixture chunk is capped at 100 rows and runs in one bounded transaction. Before the row merge loop, persistence preloads:
- the season once;
- all involved canonical teams by ID;
- all existing provider external-ID mappings for the chunk;
- canonical candidate matches for the season, involved teams and chunk kickoff range;
- provider identity mappings for candidate matches and primary-provider identities.

Candidate orientation and the six-hour kickoff tolerance are checked in memory. Ambiguous matches and identity conflicts are rejected before canonical mutation. New provider identity rows are accumulated and inserted with `saveAll` at the end of the chunk. The row loop must not perform provider-ID or candidate-match repository lookups.

A failed chunk rolls back as a unit; orchestration records that chunk as failed and continues with the next chunk. This is intentional: the persistence boundary is atomic at the bounded chunk size, not per row.

## Snapshot provenance
Match statistics source snapshots are append-only audit records, scoped by match, team and provider. The canonical `fb_match_advanced_stats` row remains the analytics projection. Enrichment lifecycle remains independently keyed by match/provider/resource. Field provenance and raw snapshots serve different purposes: the former explains each accepted canonical metric, while the latter preserves provider payload history.

## Rollout
1. Back up the match and source-snapshot tables.
2. Run `phase4-persistence-merge.sql`.
3. Deploy code with cron disabled.
4. Reconcile duplicate provider IDs and ambiguous cross-provider fixture candidates.
5. Run the Phase 5 integration/conflict and lock-contention suite before enabling cron.
