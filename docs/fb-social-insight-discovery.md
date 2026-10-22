# Social-insight candidate discovery

## Endpoint

`GET /api/fb/analytics/matches/social-insights`

Returns upcoming scheduled fixtures ranked by editorial data readiness. This complements, and does not replace, the single-match endpoint:

- `GET /api/fb/analytics/matches/{matchId}/social-insight`

### Query parameters

- `from`: optional ISO-8601 instant; defaults to the current server time.
- `to`: optional ISO-8601 instant; defaults to seven days after `from`.
- `leagueId`: optional internal league ID.
- `limit`: optional result count, defaults to 20 and is clamped to 1–50.

The date range must be positive and no longer than 60 days. Only `SCHEDULED` matches are returned. The query evaluates a bounded pool of up to 100 fixtures in kickoff order, then returns the highest readiness scores, with kickoff as the tie-breaker. It does not claim to rank every fixture in a large date range.

Example:

```http
GET /api/fb/analytics/matches/social-insights?from=2026-10-22T00:00:00Z&to=2026-10-29T00:00:00Z&limit=20
```

## Readiness score

The score is an editorial data-availability heuristic out of 100, not a prediction probability, expected outcome, or measure of model calibration.

- Competition and both teams linked: 10 points.
- Historical form: up to 15 points per team, based on usable matches, capped at five matches per team for scoring.
- Venue-specific history: up to 5 points per team.
- Complete persisted 1X2 model probabilities: 20 points.
- A complete 1X2 market row: 10 points. This is optional editorial context, not a betting recommendation.
- Historical xG-for and xG-against for both teams: 20 points.

Readiness labels:
- `READY`: 75–100
- `PARTIAL`: 45–74
- `LIMITED`: 0–44

Each candidate includes `readinessFactors`, `missingData`, historical sample counts, and boolean availability fields. Missing odds or xG does not exclude a fixture.

## Limitations

- Persisted predictions are used only as an availability signal; this endpoint does not calculate or validate new predictions.
- Historical sample quality is approximated by sample counts; this score does not assess opponent strength, data freshness, calibration, or competition importance.
- Missing xG is treated as unavailable, not inferred from goals.
- Use the single-match endpoint for full editorial insights after selecting a candidate.
