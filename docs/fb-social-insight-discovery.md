# Social-insight candidate discovery

## Endpoint

`GET /api/fb/analytics/matches/social-insights`

Returns upcoming scheduled fixtures ranked by editorial data readiness. This is a discovery endpoint; it does not replace the single-match analysis endpoint:

- `GET /api/fb/analytics/matches/{matchId}/social-insight`

### Query parameters

- `from`: optional ISO-8601 instant; defaults to the current server time.
- `to`: optional ISO-8601 instant; defaults to seven days after `from`.
- `leagueId`: optional internal league ID.
- `limit`: optional result count, defaults to 20 and is clamped to 1–50.

The date range must be positive and no longer than 60 days. Only matches with status `SCHEDULED` are returned. The query examines a bounded pool of the first 100 fixtures in kickoff order, scores those fixtures, and returns up to `limit` candidates by descending readiness score, then kickoff time. It does not claim to rank every fixture in a large date range.

Example:

```http
GET /api/fb/analytics/matches/social-insights?from=2026-10-22T00:00:00Z&to=2026-10-29T00:00:00Z&limit=20
```

## Readiness score

The score is an editorial data-availability heuristic out of 100, not a prediction probability, expected outcome, or measure of model calibration.

- Competition and both teams linked: 10 points.
- Historical form: up to 15 points per team, based on usable matches, capped at five matches per team for scoring.
- Venue-specific history: up to 5 points per team, capped at five matches per team.
- Complete persisted 1X2 model probabilities: 20 points.
- A complete 1X2 market row: 10 points. This is optional editorial context, not a betting recommendation.
- Historical xG-for and xG-against for both teams: 20 points.

Readiness labels:
- `READY`: 75–100
- `PARTIAL`: 45–74
- `LIMITED`: 0–44

Each candidate includes positive `readinessFactors`, `missingData`, sample counts, and boolean availability fields so callers can explain why a match ranked as it did. Missing odds or xG does not exclude a fixture.

## Limitations

- Existing persisted predictions are used only as an availability signal; this endpoint does not calculate or validate new predictions.
- Historical sample quality is approximated by sample counts; this score does not assess opponent strength, data freshness, calibration, or competition importance.
- A missing persisted xG sample should be treated as unavailable, not inferred from goals.
- Use the existing single-match endpoint to retrieve full editorial insights after selecting a candidate.
