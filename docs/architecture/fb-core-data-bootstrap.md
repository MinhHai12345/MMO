# FB Core Data Bootstrap and Incremental Sync

## Provider ownership

| Dataset | Initial import | Incremental import | Identity |
|---|---|---|---|
| League/season/team/fixture Core | Football-Data free tier, seasons starting 2023 | Current active season only | `FOOTBALL_DATA` external-ID mapping rows |
| Historical/current Core fixtures | Football-Data.co.uk CSV, seasons starting 2014 | Current season only | `FOOTBALL_DATA_UK` external-ID mapping rows |
| Advanced match statistics, xG and player statistics | Match enrichment workflow | Finished matches missing or requiring refresh | Provider-specific match/player external IDs |

Football-Data free-tier season discovery is intentionally restricted by `FootballDataMapper` to start years 2023 and later. Football-Data.co.uk is a separate CSV adapter and is not routed through the free-tier API provider.

## First bootstrap

`FootballDataBootstrapJob` invokes `FootballDataBootstrapServiceImpl`. The service checks durable `fb_provider_sync_checkpoints` rows before choosing a mode.

For a provider without the `CORE_INITIAL_BACKFILL` checkpoint:

1. Fetch that provider's league catalogue and persist provider-specific league IDs.
2. Persist season mappings (Football-Data returns seasons as part of its competition payload).
3. Fetch team catalogues for all mapped seasons.
4. Fetch fixtures for all mapped seasons.
5. Refresh standings where supported.
6. Import Football-Data.co.uk CSV for every mapped league from 2014 through the current season.
7. Save the initial-backfill checkpoint only after the provider's required Core stages complete.

The free-tier mapper exposes 2023-current seasons only; the CSV importer creates or reuses the canonical season rows from 2014-current.

## Subsequent bootstrap and scheduled sync

After the initial checkpoint exists:

- Provider league/season metadata is refreshed.
- Team and fixture ingestion use only active seasons.
- Football-Data owns the active/current-season flag for its season catalogue. Historical seasons are inactive for scheduled sync.
- Football-Data.co.uk Core import fetches only the current season; historical CSV files are not re-downloaded.
- Football-Data.co.uk enrichment is a separate Match Enrichment stage and also switches to current-season-only after its own durable checkpoint.
- The regular `FootballDataSyncJob` runs every six hours and also calls the CSV current-season method only.
- `MatchEnrichmentJob` remains independent. Once the Football-Data UK Core checkpoint exists, its first run imports CSV statistics, xG and odds for 2014-current; it writes a separate `MATCH_ENRICHMENT_INITIAL` checkpoint only after that pass succeeds.
- Later enrichment runs import Football-Data UK statistics, xG and odds for the current season only, then continue with the configured Understat/SofaScore and player-statistics workflows.

## Upsert and provider mapping rules

- Canonical MMO entities are shared; external IDs are not.
- Every provider gets its own league, season, team, match and player mapping rows.
- An existing provider external ID must resolve to the same canonical entity. Conflicts are rejected rather than silently reassigned.
- Football-Data is the primary owner of overlapping fixture Core fields. Football-Data.co.uk refreshes its own imported matches and fills missing fields on Football-Data-owned matches; it does not overwrite non-null primary Core values.
- Football-Data.co.uk team aliases are standardized by `MmoProviderDataStandardizer`. Stable synthetic provider IDs are derived from canonical team identity because CSV rows do not supply stable numeric team IDs.
- Fixture IDs are derived from league code, season and canonical home/away team IDs, so historical team-name aliases do not create duplicate fixture mappings.
- A CSV season is marked complete only when it contains rows and all rows are matched. Incomplete seasons are retried by rerunning the initial bootstrap; already completed historical seasons are skipped using their season mapping marker.

## Recovery

If the initial backfill fails, its provider checkpoint is not written. Re-running the bootstrap safely upserts already imported records and retries incomplete work. A completed initial backfill is not repeated by later scheduled jobs.
