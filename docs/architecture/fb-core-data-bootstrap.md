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

Historical backfill decisions are made independently for the selected team and fixture providers:

1. Fetch that provider's league catalogue and persist provider-specific league IDs.
2. Persist season mappings (Football-Data returns seasons as part of its competition payload).
3. Fetch team catalogues for all mapped seasons.
4. Fetch fixtures for all mapped seasons.
5. Refresh standings where supported.
6. Import Football-Data.co.uk CSV for every mapped league from 2014 through the current season.
7. Save separate `CORE_BACKFILL_TEAMS` and `CORE_BACKFILL_FIXTURES` checkpoints only after each resource's full historical import succeeds. A failure in one resource does not mark another resource complete.
8. Save the Football-Data UK `CORE_INITIAL_BACKFILL` checkpoint only after its CSV historical import reports success. The provider's own season markers allow completed seasons to be skipped on retry.

The free-tier mapper exposes 2023-current seasons only; the CSV importer creates or reuses the canonical season rows from 2014-current. If Football-Data UK is disabled, its stage is explicitly skipped and does not hold up the API-provider bootstrap.

## Subsequent bootstrap and scheduled sync

The jobs have strict ownership:

- `FootballDataBootstrapJob` is the historical initialization/recovery entry point. It retries incomplete historical team/fixture resources and writes their provider-specific checkpoints only after successful stages. It never performs current-season refreshes. Once the configured team and fixture providers have separate historical resource checkpoints and Football-Data UK has completed its CSV checkpoint (or is disabled), the job exits immediately. It does not refresh catalogues, teams, fixtures or standings.
- `FootballDataSyncJob` is the only recurring Core synchronization path (every six hours). It refreshes provider catalogues, active seasons' teams and fixtures, standings and upcoming odds. It also calls Football-Data UK current-season Core import only.
- `MatchEnrichmentJob` is independent of Core synchronization. It imports match statistics, xG, odds and player statistics through configured enrichment providers. Historical enrichment and Core fixture identity must never be coupled.
- `Season.current` describes the provider's current season. `Season.active` is the MMO operational selection used by recurring jobs; it is initialized from the provider's current flag when a season is first created, but must not be overwritten on every catalogue refresh because operators may intentionally activate a historical season.
- Bootstrap and recurring sync are separate Quartz JobDetails, so `@DisallowConcurrentExecution` on either job alone does not serialize them against each other. A shared database-backed ingestion lock is required before running bootstrap and sync concurrently in a multi-node deployment. Until that lock is introduced, operators should avoid manually launching bootstrap during a running Core sync.

Football-Data UK enrichment has its own checkpoint. Its initial enrichment pass imports CSV statistics, xG and odds for supported historical seasons; subsequent runs process the current season (and matches selected by the enrichment workflow).

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
