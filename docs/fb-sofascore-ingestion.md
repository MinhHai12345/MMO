# SofaScore ingestion

## Architecture

SofaScore uses the existing provider-neutral ingestion pipeline. The client parses typed response DTOs; provider adapters map source data to provider-neutral league, team, fixture, and standings models; existing persistence services write canonical entities and provider external-ID mappings. No SofaScore-specific domain entities are introduced.

## Configure the full ingestion workflow

Keep API-Football and football-data.org enabled as they are today. Select SofaScore independently for each resource by setting the corresponding environment variables:

- INGESTION_LEAGUE_PROVIDER=SOFA_SCORE
- INGESTION_TEAM_PROVIDER=SOFA_SCORE
- INGESTION_FIXTURE_PROVIDER=SOFA_SCORE
- INGESTION_STANDINGS_PROVIDER=SOFA_SCORE

Odds provider selection is independent; this change does not require switching odds away from API-Football.

Set SOFASCORE_TOURNAMENT_IDS to comma-separated SofaScore unique tournament IDs (for example, 17 for the Premier League) to bound the initial catalogue sync. If omitted, the catalogue endpoint may return a large set of competitions and the initial sync can be slow. Optional controls:

- SOFASCORE_TOURNAMENT_REGION (default EN)
- SOFASCORE_MAX_EVENT_PAGES (default 20 per direction, finished and upcoming)
- SOFASCORE_API_URL (default https://api.sofascore.com/api/v1/)
- SOFASCORE_WEB_URL (default https://www.sofascore.com)

The SofaScore season matcher canonicalizes labels such as 26/27, 2026/2027, and 2026 to the same start year used by local Season records. Fixture ingestion reads paginated last and next season-event endpoints rather than making one request per round. Event IDs are deduplicated before persistence.

## Workflow

1. Existing cronjobs run league/season ingestion.
2. Team ingestion uses the selected provider's season standings to discover teams and register team external IDs.
3. Fixture ingestion loads finished and upcoming season event pages, maps provider home/away team IDs to canonical teams, and upserts matches idempotently.
4. Standings ingestion maps SofaScore rows to the local standings model.
5. Existing analytics jobs consume canonical match and statistics data; content generation and publication remain separate workflow stages.

No additional scheduler is introduced; existing FBCronjobInitial and CronJobService remain responsible for scheduling.

## Reliability and limitations

- HTTP 403/429 and upstream 5xx responses now fail the SofaScore request visibly instead of being converted into empty data after a fixed 60-second sleep. The ingestion job can therefore record a failure rather than silently mark a stage successful.
- HTTP 404 remains an empty response for endpoints where missing pages/rounds are expected; required catalogue/season/standings resources validate the response.
- SofaScore website endpoints are undocumented and are not an official public API. They can change, throttle or block requests. Use compliant request pacing and verify SofaScore's applicable terms before production use.
- Playwright Chromium must be installed in the runtime. CI passing validates compilation/tests, not live access or the provider's current response shape.
- Verify logs and fb_crawler_health entry SOFA_SCORE_CORE_BOOTSTRAP after the first run.
