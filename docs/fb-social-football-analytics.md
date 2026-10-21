# FB social football analytics

## Purpose

Expose a structured, read-only match insight payload for social-content generation. This is an analytics context API, not an automatic publisher and not a guarantee that a model prediction is correct.

## Endpoint

`GET /api/fb/analytics/matches/{matchId}/social-insight`

The response contains competition and fixture metadata, team recent-form summaries, total standings context, available model probabilities, and a normalized 1X2 market snapshot when one complete odds row is present.

## Editorial signals

- Recent form and scoring/conceding rates.
- League position and points where the TOTAL standing is available.
- Model's highest-probability outcome, phrased as a probabilistic assessment.
- Large model-versus-market probability differences as a discussion angle only.
- Data warnings for missing history, prediction probabilities, odds or match xG.

## Data and safety rules

- The feature service must only use completed matches before the target kickoff.
- Market implied probabilities are normalized by the sum of reciprocal odds to remove the bookmaker overround for comparison.
- The service selects one complete 1X2 row; it does not mix odds across bookmakers/markets.
- Missing values stay null and produce warnings; they are not replaced with invented stats.
- Odds divergence is editorial context, not betting advice. Do not claim an edge or certainty.
- xG claims must not be published if match-level xG is incomplete.
- This endpoint does not publish posts, modify predictions, or mutate stored data.

## Next analytics domains

This is a foundation, not the final limit of modern football analysis. High-value follow-ups are team advanced metrics (PPDA, field tilt, shots and shots on target, big chances, set pieces), player availability/lineups and player metrics, shot-level xG if licensed data is available, tactical matchup indicators, and post-match performance summaries. Those features should only be exposed when source coverage and persistence are reliable.
