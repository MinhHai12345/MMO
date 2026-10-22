# FB advanced team and matchup analytics

## Endpoint

`GET /api/fb/analytics/matches/{matchId}/social-insight`

The existing response now includes two additional analysis areas:

- `home` and `away` expose venue-specific scoring and concession averages in addition to overall form, goals, points, win rate, clean-sheet rate, scoring rate, and optional xG averages.
- `matchup` compares each side's historical attack rates against the opponent's historical concession rates. Where available, it also compares home/away venue splits and team xG-for against opponent xG-against.

## Matchup payload

- `summary`: concise direction from available statistical indicators, not a match-result prediction.
- `xgAvailable`: true only when both teams have complete xG-for and xG-against feature values.
- `signals`: each comparable dimension includes an edge label, human-readable evidence, and a sample-size-based confidence band.

Signal edge labels:
- `ATTACKING_SIDE`: attack rate is at least 0.15 per match above the opponent concession rate.
- `OPPONENT_DEFENCE`: attack rate is at least 0.15 per match below the opponent concession rate.
- `BALANCED`: absolute difference is below the threshold.

Confidence bands are descriptive sample-size hints, not statistically calibrated confidence intervals.

## Important limitations

This is a **statistical matchup**, not event-level tactical analysis. The current provider data does not support reliable claims about formations, pressing intensity, buildup patterns, player roles, injuries, or individual duels. The API explicitly warns when historical xG is incomplete and falls back to score-derived indicators. Do not invent missing metrics or frame these signals as certainty or betting advice.
