# Dynamic ingestion provider selection

Ingestion jobs no longer need to be tied to one upstream provider. The resource-specific ingestion services resolve a provider through `ProviderRegistry`; a job can either use the configured default or override it for one execution.

## Default routing

The defaults live under `app.ingestion.providers` in `application.yml`. The corresponding environment variables are optional:

- `INGESTION_LEAGUE_PROVIDER`
- `INGESTION_TEAM_PROVIDER`
- `INGESTION_FIXTURE_PROVIDER`
- `INGESTION_STANDINGS_PROVIDER`
- `INGESTION_ODDS_PROVIDER`

If omitted, the application uses Football Data for league, team, fixture and standings, and API-Football for odds.

## Override a single job execution

Send a JSON body to the existing manual trigger endpoint:

```http
POST /cronjobs/{id}/start
Content-Type: application/json

{
  "provider": "SOFA_SCORE"
}
```

The provider is passed through Quartz's trigger `JobDataMap` and is not persisted as a new default. Omit the body or provider field to use the configured default.

Accepted provider names are `SOFA_SCORE`, `FOOTBALL_DATA`, `API_FOOTBALL`, and `UNDER_STATS` (case-insensitive; separators are ignored).

## Bootstrap per-resource overrides

The bootstrap job supports different providers for different resources:

```http
POST /cronjobs/{id}/start
Content-Type: application/json

{
  "provider.league": "FOOTBALL_DATA",
  "provider.team": "FOOTBALL_DATA",
  "provider.fixture": "SOFA_SCORE",
  "provider.standings": "FOOTBALL_DATA"
}
```

Resource-specific keys take precedence over the generic `provider` key. The bootstrap service uses the same ingestion services and persistence pipeline as standalone jobs.

## Validation behavior

- An explicitly requested provider must have a registered adapter for the requested resource.
- Disabled API providers fail the job instead of silently reporting a successful no-op.
- A provider selection applies to that execution only; scheduled executions continue to use the configured defaults.
- Provider-specific API credentials and settings remain under `app.ingestion`.
