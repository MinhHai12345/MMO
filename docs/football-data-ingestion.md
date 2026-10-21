# Football core-data ingestion

The core-data pipeline can use API-Football or football-data.org for league, team, fixture/result and standings resources. Providers are selected independently; odds remain on API-Football unless another provider explicitly implements the odds contract.

## football-data.org configuration

Create an API token at https://www.football-data.org/ and set:

- `FOOTBALL_DATA_ENABLED=true`
- `FOOTBALL_DATA_API_KEY=<your-token>`
- `FOOTBALL_DATA_COMPETITION_IDS=<comma-separated numeric competition IDs>`
- `FOOTBALL_DATA_SEASONS=<comma-separated season start years>` (optional; empty means current season only)

Example for local PowerShell (replace the token and database credentials):

```powershell
$env:FOOTBALL_DATA_ENABLED = "true"
$env:FOOTBALL_DATA_API_KEY = "<your-token>"
$env:FOOTBALL_DATA_COMPETITION_IDS = "2021,2001"
$env:FOOTBALL_DATA_SEASONS = "2021,2022,2023,2024,2025,2026"
$env:INGESTION_LEAGUE_PROVIDER = "FOOTBALL_DATA"
$env:INGESTION_TEAM_PROVIDER = "FOOTBALL_DATA"
$env:INGESTION_FIXTURE_PROVIDER = "FOOTBALL_DATA"
$env:INGESTION_STANDINGS_PROVIDER = "FOOTBALL_DATA"
$env:INGESTION_ODDS_PROVIDER = "API_FOOTBALL"
mvn test
mvn spring-boot:run
```

The IDs above are examples: `2021` is the Premier League and `2001` is the UEFA Champions League in football-data.org's API. Configure only competitions available to your token. The provider uses the v4 API and authenticates with the `X-Auth-Token` header.

To keep API-Football as a fallback for odds, leave `INGESTION_ODDS_PROVIDER=API_FOOTBALL`. football-data.org does not implement the project's odds-provider contract.

## Using API-Football instead

Set the following values and keep the provider selectors at `API_FOOTBALL`:

- `API_FOOTBALL_ENABLED=true`
- `API_FOOTBALL_API_KEY=<your-key>`
- `API_FOOTBALL_LEAGUE_IDS=<comma-separated API-Football league IDs>`

## Ingestion behavior and limitations

- Provider selection is resource-specific. League, team, fixture, standings and odds can be configured independently.
- External IDs are stored with their provider, so IDs from football-data.org do not overwrite IDs from API-Football.
- `FOOTBALL_DATA_SEASONS` uses the season start year sent to football-data.org, not the end year. For example, `2021` means the 2021/22 season. When configured, the bootstrap creates missing local season records and fetches teams and fixtures for each configured year; an empty value preserves current-season-only behavior.
- Historical seasons increase request usage: each configured competition/year can require a teams request and a fixtures request. With the Free API quota, start with one competition and a small season range, then expand after observing request pacing and 429 logs. Only configure seasons accessible to your token.
- football-data.org availability and historical coverage depend on the account tier and competition.
- The integration currently maps competitions, teams, fixtures/results and standings. It does not provide odds, xG, lineups or detailed event statistics.
- The API is rate-limited. Avoid aggressive polling and inspect provider response errors (especially HTTP 429) before retrying.
- Never commit provider tokens or database credentials.
