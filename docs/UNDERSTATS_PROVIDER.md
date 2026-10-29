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

The job first syncs Understat match IDs and xG to existing canonical fixtures. A fixture is linked
only when its team identities and kickoff identify one unique match; Understat never creates a
canonical match. Teams are resolved by existing provider mapping first, then by a unique exact
case-insensitive canonical team-name match. Ambiguous or unmatched teams/fixtures are skipped and
logged for explicit mapping correction. Team-history metrics are upserted into
`fb_match_advanced_stats` by `(match_id, team_id)`; the match's `home_xg`/`away_xg` are
updated from Understat when present. League payloads are cached in memory for ten minutes to avoid
re-downloading the same large season payload for every team.

Do not treat null xG on a scheduled fixture as zero, and only use matches completed before a
prediction cutoff when building historical features. A challenge/non-JSON response is logged as
a provider failure; the client does not attempt to bypass access controls.
