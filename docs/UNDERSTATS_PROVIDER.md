# Understat historical statistics provider

The provider is registered under `Provider.UNDER_STATS` and is selected independently through
`app.ingestion.providers.historical-stats`. It reads Understat's public HTML pages and extracts
the embedded `datesData` (league fixtures) and `teamData` (team match history) JSON payloads.

## Configuration

```properties
UNDER_STATS_ENABLED=true
UNDER_STAT_BASE_URL=https://understat.com
INGESTION_HISTORICAL_STATS_PROVIDER=UNDER_STATS
```

Use Understat URL slugs for league and team names, not football-data.org IDs. Common league slugs
include `EPL`, `La_liga`, `Bundesliga`, `Serie_A`, and `Ligue_1`. The `season` argument is the
calendar year used in the Understat page URL.

## Provider contract

- `fetchLeagueMatches(leagueSlug, season)`: match IDs, team IDs/names, kickoff, score, xG and
  optional forecast probabilities from the league page.
- `fetchTeamHistory(teamSlug, season)`: per-match xG/xGA, npxG/npxGA, PPDA components, deep
  entries, score/result, xPts and non-penalty goal difference.
- `fetchLeagueMonthlyStats(leagueSlug, season, month)`: averages computed from completed matches
  with score and xG available in the requested month. It returns empty when no usable sample exists.

The provider returns source-neutral records and does not write to the database. Persistence and
mapping to MMO's canonical fixture/team IDs belong in the ingestion service. Do not treat null xG
on a scheduled fixture as zero, and only use matches completed before a prediction cutoff when
building historical features.

Understat page markup is not a stable public API. Access failures, page-layout changes and missing
embedded variables are surfaced as clear exceptions; the implementation does not attempt to bypass
CAPTCHA or access controls.
