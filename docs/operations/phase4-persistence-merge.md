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
- Each accepted field update records provider, dataset, source update time and fetch time in `fb_match_field_provenance`.

Do not derive points or scores from partial source data. Null remains unknown; zero is a real value.

## Batch persistence
The fixture persistence API accepts a season batch. Implementations must preload provider external IDs and canonical candidates once per batch, persist canonical rows in bounded chunks, then bulk insert missing external-ID mappings. Per-row errors are reported without losing the rest of the batch.

## Snapshot provenance
Match statistics source snapshots are append-only audit records, scoped by match, team and provider. The canonical `fb_match_advanced_stats` row remains the analytics projection. Enrichment lifecycle remains independently keyed by match/provider/resource.

## Rollout
1. Back up the match and source-snapshot tables.
2. Run `phase4-persistence-merge.sql`.
3. Deploy code with cron disabled.
4. Reconcile duplicate provider IDs and ambiguous cross-provider fixture candidates.
5. Run the Phase 5 integration/conflict and lock-contention suite before enabling cron.
