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

The provider returns source-neutral records and does not write to the database. Persistence and
mapping to MMO's canonical fixture/team IDs belong in the ingestion service. Do not treat null xG
on a scheduled fixture as zero, and only use matches completed before a prediction cutoff when
building historical features.
