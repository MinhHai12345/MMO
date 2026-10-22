# FB advanced team and matchup analytics

## Endpoint

`GET /api/fb/analytics/matches/{matchId}/social-insight`

The existing response now includes two additional analysis areas:

- `home` and `away` expose venue-specific scoring and concession averages in addition to overall form, goals, points, win rate, clean-sheet rate, scoring rate, and optional xG averages.
- `matchup` compares each side's historical attack rates against the opponent's historical concession rates. Where available, it also compares home/away venue splits and team xG-for against opponent xG-against.

## Matchup payload

- `summary`: summarizes the count of higher attack rates, higher opponent concession rates, and similar comparisons. It does not claim a home/away result advantage.
- `xgAvailable`: true only when both teams have complete xG-for and xG-against feature values.
- `signals`: each comparable dimension includes an explicit rate-comparison label, human-readable evidence, and a sample-size-based confidence band.

Signal edge labels (threshold: 0.15 per match):
- `ATTACK_RATE_HIGHER`: the attack rate is more than 0.15 above the opponent's concession rate.
- `OPPONENT_CONCESSION_RATE_HIGHER`: the opponent's concession rate is more than 0.15 above the attack rate.
- `SIMILAR_RATES`: the absolute difference is at most 0.15.

These labels describe the two rates being compared. They do not mean that a team is predicted to win, and a high opponent-concession rate should not be described as a strong defence. The summary is generated from all available signals so it cannot claim the matchup is balanced when all comparisons point in the same rate direction.

Confidence bands are descriptive sample-size hints, not statistically calibrated confidence intervals.

## Important limitations

This is a **statistical matchup**, not event-level tactical analysis. The current provider data does not support reliable claims about formations, pressing intensity, buildup patterns, player roles, injuries, or individual duels. The API explicitly warns when historical xG is incomplete and falls back to score-derived indicators. Do not invent missing metrics or frame these signals as certainty or betting advice.
