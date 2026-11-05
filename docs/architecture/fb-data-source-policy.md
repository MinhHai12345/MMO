# Football data source policy

## Purpose

MMO routes ingestion by **dataset + season**, not by a single global provider.
This prevents a provider that supplies only enrichment data from replacing canonical
fixture or team data owned by another provider.

The policy is implemented in:

- `ingest.policy.Dataset`
- `ingest.policy.ProviderRoute`
- `ingest.policy.DataSourcePolicy`

## Ownership matrix

The season key is the first calendar year (for example, `2024/2025` -> `2024`).

| Dataset | 2014–2022 | 2023 onward |
|---|---|---|
| League canonical metadata | Football-Data API | Football-Data API |
| Season metadata | Football-Data UK | Football-Data API |
| Team identity/core | Football-Data UK | Football-Data API |
| Fixture identity, kickoff, result, status | Football-Data UK | Football-Data API |
| Standings | Unsupported | Football-Data API |
| Match statistics | Football-Data UK | Football-Data UK |
| Match xG | Football-Data UK | Football-Data UK |
| Historical odds | Football-Data UK | Football-Data UK |
| Upcoming odds | Disabled | Disabled |

Football-Data UK routing is restricted to competitions currently mapped by the CSV importer:
Premier League, Championship, Bundesliga, Serie A, LaLiga and Ligue 1.
Other competitions (for example UEFA Champions League, FIFA World Cup and Russian Premier League)
are not silently routed to Football-Data UK when no CSV mapping exists.

## Overlap and fallback rules

From 2023 onward Football-Data API is the primary owner of Core. Football-Data UK is a
secondary contributor for fixtures only when the primary provider has not yet mapped that
fixture. Once the Football-Data external identity exists:

- UK must not replace kickoff, status, full-time/half-time scores or other fixture Core fields.
- UK may fill a missing Core value only; it must not replace a populated primary value.
- UK continues to write its own match-statistics and historical-odds datasets.
- UK owns xG because the Football-Data API workflow does not provide the corresponding CSV xG dataset.

When Football-Data later imports a fixture previously created by the UK fallback, the
Football-Data API becomes the authoritative Core source and its mapping is attached to the
same canonical match.

Team matching uses a unique standardized canonical name when Football-Data sees a team
previously created by the UK fallback. Ambiguous matches are skipped rather than creating
duplicate canonical teams.

## Workflow integration

- `IngestionServiceSupport` filters active and historical season lists using the dataset route.
- `TeamIngestionServiceImpl` and `FixtureIngestionServiceImpl` request only seasons owned by
  their selected provider.
- `TeamPersistenceServiceImpl`, `FixturePersistenceServiceImpl` and
  `LeaguePersistenceServiceImpl` enforce ownership again at persistence boundaries.
- `StandingsPersistenceServiceImpl` receives the selected provider and rejects writes from a
  provider that does not own standings for that season.
- UK statistics, xG and historical odds each have persistence-boundary ownership checks.
- `FootballDataUkSyncService` uses the policy for team creation, fixture fallback and fixture Core
  updates. Its incremental Core and enrichment jobs refresh only operator-selected active seasons,
  not every historical CSV season.
- A newly discovered current season defaults to active; subsequent provider syncs never change
  the operator-controlled active flag.

The persistence checks are intentional: routing alone is not sufficient protection against
manual provider overrides or future callers that bypass the normal orchestration.

## Extending provider coverage

When adding a provider or a competition:

1. Add its coverage to `DataSourcePolicy` only after verifying the provider supports that
   dataset and season range.
2. Add a canonical league mapping and provider-specific `identify` value.
3. Add tests for the primary owner, secondary contributors and unsupported seasons.
4. Ensure the provider mapper writes provider-specific external IDs and does not normalize
   names inside application services.
5. Keep upcoming odds disabled until a supported, enabled provider is explicitly introduced.


## Backfill reliability and checkpoints

Football-Data UK downloads distinguish coverage gaps from provider failures:

| Outcome | Checkpoint state | Backfill behavior |
|---|---|---|
| HTTP 404/410 or empty response | `NOT_AVAILABLE` | Skip this league-season without failing the whole backfill |
| Valid CSV with rows and no unmatched fixtures | `COMPLETED` | Mark the dataset complete |
| Valid CSV with unmatched rows | `PARTIAL` | Keep it eligible for a later retry |
| Invalid header, HTML/challenge page or malformed CSV | `FAILED` | Do not mark complete |
| HTTP 429, 5xx or transient network failure | `FAILED` after bounded retry | Preserve the failure reason and retry on a later run |

Checkpoint identity is stored as provider + dataset + canonical league ID + season. Core
backfill tracks `SEASON`, `TEAM` and `FIXTURE` independently; enrichment tracks
`MATCH_STATISTICS`, `MATCH_XG` and `HISTORICAL_ODDS` independently. States are
`PENDING`, `RUNNING`, `COMPLETED`, `PARTIAL`, `FAILED` and `NOT_AVAILABLE`.

The new checkpoint status column is nullable so legacy checkpoint rows remain readable as
completed. Existing `UK_CORE_<leagueId>_<year>` and legacy `:CORE2` markers are migrated
to the scoped dataset keys when encountered.
