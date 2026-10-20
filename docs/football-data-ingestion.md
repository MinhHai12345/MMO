# API-Football core data ingestion

The first end-to-end ingestion pass intentionally focuses on the current season and core data:

1. leagues and season metadata
2. teams for current seasons
3. current-season fixtures and scores
4. current-season standings

The pipeline is orchestrated by `FootballDataBootstrapService` and is invoked by the Quartz job `MatchSeasonFixtureSyncJob`. Routine fixture and result jobs refresh smaller date windows. Historical fixture backfill, advanced match statistics, and analytics tuning are intentionally separate follow-up work.

## Required environment

Configure the following environment variables before starting the application:

- `API_FOOTBALL_ENABLED=true`
- `API_FOOTBALL_API_KEY=<your API-Football key>`
- `API_FOOTBALL_LEAGUE_IDS=<comma-separated league IDs>`
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` for MySQL

Example for local PowerShell (replace the sample IDs with the leagues you actually want):

```powershell
$env:API_FOOTBALL_ENABLED = "true"
$env:API_FOOTBALL_API_KEY = "<your-api-key>"
$env:API_FOOTBALL_LEAGUE_IDS = "39,140"
$env:DB_URL = "jdbc:mysql://localhost:3306/fb_ai?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "<your-db-password>"
mvn test
mvn spring-boot:run
```

Do not commit API keys or database credentials. The application deliberately rejects the placeholder key `mock`, and the bootstrap fails fast when the key or league list is missing. Keep the league list small enough for the API quota of your subscription.

## Run the bootstrap once

The application registers the Quartz job `MatchSeasonFixtureSyncJob` in the `cron_jobs` table. To trigger it immediately, find its ID and call the existing Quartz endpoint:

```sql
SELECT id, job_name, job_status
FROM cron_jobs
WHERE job_name = 'MatchSeasonFixtureSyncJob'
  AND job_group = 'FBJobGroup';
```

Then call:

```http
POST http://localhost:8188/cronjobs/{id}/start
```

The job runs the core pipeline in dependency order. Review application logs for the start/completion messages and ingestion counters, then verify records in the `leagues`, `seasons`, `teams`, `matches`, and standings tables (actual table names may follow the entity mappings).

## Expected routine refreshes

- `MatchFixtureSyncJob`: today through the next 14 days (UTC)
- `MatchResultSyncJob`: the previous 7 days through today (UTC)
- `TeamSyncJob`: current-season team metadata
- `LeagueSyncJob`: configured league and season metadata

These jobs are safe to rerun: fixture persistence updates the canonical match and its provider mapping rather than blindly inserting a duplicate fixture. Provider calls are performed outside the short persistence transactions.

## Scope and limits

- This first pass does not claim historical fixture backfill or complete match-statistics ingestion.
- The bootstrap requires league IDs explicitly to avoid an accidental all-leagues import and unexpected API quota consumption.
- If a fixture is skipped because a team or league mapping is missing, check the configured league IDs and the ingestion logs before running analytics.
