# SofaScore ingestion

## Architecture

SofaScore core data uses the same provider-neutral pipeline as the other sources:

1. `sofascore.client.SofaScoreClientImpl` owns outbound SofaScore requests and parses typed SofaScore DTOs through Playwright. The `SofaScoreClient` interface isolates provider adapters from transport details; browser lifecycle and fetch helpers live beside the client.
2. `SofaScoreLeagueProvider`, `SofaScoreTeamProvider`, and `SofaScoreFixtureProvider` map source DTOs into `ProviderLeague`, `ProviderTeam`, and `ProviderFixture`.
3. `ProviderRegistry` resolves the selected source.
4. Existing ingestion and persistence services upsert canonical `League`, `Season`, `Team`, and `Match` records and register provider IDs through `ExternalIdMappingService`.
5. `SofaScoreCrawlStrategy` is limited to finished-match statistics and xG enrichment.

Provider IDs must remain in the corresponding external-ID mapping tables. SofaScore must not introduce parallel domain entities or legacy provider-ID columns.

## Initial synchronization

The existing `MatchStatisticsSyncJob` first calls `SofaScoreBootstrapService`. If SofaScore mappings are absent, it imports leagues/seasons, current-season teams, and all current-season fixtures, then fetches statistics for eligible finished matches.

The bootstrap intentionally imports the full current season rather than only upcoming fixtures; historical finished fixtures are required for xG and post-match analysis. Re-running the job after a successful bootstrap skips the expensive core import and continues statistics ingestion.

The current-season fixture import uses `FixtureIngestionService.syncFixturesForProvider(SOFA_SCORE, null, null)`. Persistence remains idempotent through provider external IDs and canonical fixture matching.

## Scope controls

- `app.sofa-score.tournament-region` selects the SofaScore default tournament catalogue region (default `EN`).
- `app.sync.tournament-ids` can bound the first import to known SofaScore unique-tournament IDs. An empty set means all tournaments returned by the configured catalogue endpoint.
- `app.sync.max-rounds` bounds the number of rounds queried per season (default 50).

For a first production sync, configure the tournament IDs when possible to reduce upstream calls and rate-limit exposure.

## Operational notes

- The job uses the existing CronJob initialization and schedule; no new scheduler configuration is introduced.
- Playwright Chromium must be installed in the application runtime, and the runtime must have outbound access to SofaScore.
- Check job logs and the `fb_crawler_health` row for `SOFA_SCORE_CORE_BOOTSTRAP`. The bootstrap throws if it cannot persist any league, season, team, or current-season fixture mappings rather than silently reporting success.
- The repository CI can validate compilation and unit tests, but a successful live sync can only be confirmed in the deployed environment by checking the job logs and provider mapping tables.
