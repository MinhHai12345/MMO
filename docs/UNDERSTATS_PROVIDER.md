# Understat historical statistics provider

The provider is registered under `Provider.UNDER_STATS` and selected independently through
`app.ingestion.providers.historical-stats`. It calls Understat's JSON AJAX endpoints directly:

- `GET /getLeagueData/{league}/{season}` for league fixtures and per-team match history.
- `GET /getTeamData/{team}/{season}` is supported by the HTTP client for team-specific payloads.

The request sends `Accept: application/json`, `X-Requested-With: XMLHttpRequest`, and a league/team page
`Referer`. Browser session cookies are not hardcoded. If Understat returns a challenge or non-JSON
body, the client fails with an explicit error rather than attempting to bypass the access control.

## Configuration

```properties
UNDER_STATS_ENABLED=true
UNDER_STAT_BASE_URL=https://understat.com
INGESTION_HISTORICAL_STATS_PROVIDER=UNDER_STATS
```

Use Understat URL slugs for league and team names, not football-data.org IDs. Common league slugs
include `EPL`, `La_liga`, `Bundesliga`, `Serie_A`, and `Ligue_1`. The season argument is the
calendar year at the start of the season; `2026` means the 2026/27 campaign.

## Provider contract

- `fetchLeagueMatches(leagueSlug, season)`: match IDs, team IDs/names, kickoff, score, xG and
  optional forecast probabilities from the `dates` array.
- `fetchTeamHistory(leagueSlug, teamSlug, season)`: per-match xG/xGA, npxG/npxGA, PPDA components,
  deep entries, score/result, xPts and non-penalty goal difference from `teams[teamId].history`.
- `fetchLeagueMonthlyStats(leagueSlug, season, month)`: averages computed from completed matches
  with score and xG available in the requested month. It returns empty when no usable sample exists.

## End-to-end persistence workflow

The `UnderstatHistoricalStatsJob` is registered by `FBCronjobInitial` and runs every 12 hours.
It uses `app.ingestion.providers.historical-stats` to select the historical provider and only
processes seasons whose `fb_seasons.is_active` flag is true. Newly discovered current seasons are
enabled by default; existing operator choices are preserved.

Before the job can fetch a league, that canonical MMO league needs an explicit provider mapping:
`provider=UNDER_STATS`, `identify` set to the Understat key (for example `EPL`), and
`external_id` set to the stable Understat key/ID. Use the actual canonical `league_id` from
`fb_leagues`; do not match by fuzzy league name.

Example mapping (replace the placeholder ID with the canonical MMO league ID):

```sql
INSERT INTO fb_league_external_ids (league_id, provider, external_id, identify)
VALUES (<MMO_LEAGUE_ID>, 'UNDER_STATS', 'EPL', 'EPL');
```

Because `is_active` is a new operator-controlled flag, enable the seasons you want to backfill.
For existing installations, a one-time example is:

```sql
UPDATE fb_seasons SET is_active = 1 WHERE is_current = 1;
```

The job correlates Understat match IDs to existing canonical fixtures by season, kickoff window
and (for completed matches) score. Once a unique fixture is found, its home/away team records are
used to register the Understat team IDs. This avoids guessing identity from team-name spelling or
aliases. Understat never creates canonical teams or matches. Ambiguous kickoff/score combinations
and conflicting provider IDs are skipped and logged; if an ID cannot be learned from a unique
fixture, it can be mapped explicitly:

```sql
INSERT INTO fb_team_external_ids (team_id, provider, external_id)
VALUES (<MMO_TEAM_ID>, 'UNDER_STATS', '<UNDERSTAT_TEAM_ID>');
```

Team-history metrics are upserted into `fb_match_advanced_stats` by `(match_id, team_id)`;
the match's `home_xg`/`away_xg` are
updated from Understat when present. League payloads are cached in memory for ten minutes to avoid
re-downloading the same large season payload for every team.

Do not treat null xG on a scheduled fixture as zero, and only use matches completed before a
prediction cutoff when building historical features. A challenge/non-JSON response is logged as
a provider failure; the client does not attempt to bypass access controls.


## Full catalogue synchronization

UnderstatCatalogSyncJob runs daily at 00:05 UTC, before the historical-statistics job. It
iterates the six Understat league keys (EPL, La_Liga, Bundesliga, Serie_A, Ligue_1, RFPL)
and season start years from 2014 through the current UTC year. Each
getLeagueData/{league}/{season} response contains teams, players, and dates, so one request
supplies the complete catalogue for that league-season.

The persistence workflow:

1. Upserts the league in fb_leagues and registers fb_league_external_ids with
   provider=UNDER_STATS, external_id and identify equal to the Understat league key.
2. Creates the corresponding fb_seasons row when absent. The current season is active by
   default; existing operator-controlled active flags are not overwritten.
3. Upserts teams in fb_teams and registers fb_team_external_ids using Understat team IDs.
4. Upserts players in fb_players and registers fb_player_external_ids using Understat player IDs.
   Player team links are resolved through the team IDs in the same league payload.
5. Reuses an existing MMO entity only when its standardized name is an exact, unique match and
   it has no conflicting Understat mapping. Otherwise a separate provider-backed entity is created;
   no fuzzy name matching or reassignment of an existing provider ID is performed.

The catalogue sync retries transient network, HTTP 429 and HTTP 5xx failures up to three attempts
with a short backoff. HTTP 404 means that the league-season is unavailable and is skipped. Other
failed requests are logged and cause the Quartz job to finish in a failed state so the next
scheduled run can recover. The existing 12-hour UnderstatHistoricalStatsJob remains responsible
for match xG and team-history enrichment and is not replaced by the catalogue job.

Player identity, current team, and position are persisted in fb_players. Season aggregates are
stored separately in fb_player_season_stats, keyed by player, season, and provider. The table
includes appearances, minutes, goals, xG, assists, xA, shots, key passes, cards, non-penalty
goals, npxG, xGChain, and xGBuildup. This keeps historical season values from overwriting the
global player profile.
