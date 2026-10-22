# Player Analytics, Post-match Analysis, and Social Insight

## Endpoints

- `GET /api/fb/analytics/matches/{matchId}/player-analytics`
- `GET /api/fb/analytics/matches/{matchId}/post-match-analysis`
- `GET /api/fb/analytics/matches/{matchId}/social-insight`

The existing social-insight endpoint now includes `playerAnalytics`, `postMatchAnalysis` (finished matches only), and a `contentPlan`. The content plan separates `preMatchAngles`, `postMatchAngles`, `longVideoAngles`, `shortsHooks`, and `factualCaveats`.

## Player analytics

Reads existing `Player` and `PlayerMatchStats` records for the fixture. The standout-player list is ranked using a transparent editorial impact heuristic built from goals, assists, shots on target, key passes, defensive actions, duels, xG, and xA. It is not an official provider rating and should not be represented as one.

The response includes evidence for each ranked player, goal contributions, and goals-minus-xG where xG exists. Missing player rows or missing xG/xA are reported as data warnings instead of being inferred from the scoreline.

`PlayerMatchStats.team` records the team represented in that particular match, rather than relying exclusively on a player's current team after a transfer.

## Post-match analysis

- Requires a finished match with both scores to publish a result finding.
- Compares match-level xG only when both home and away values exist.
- Surfaces material score-versus-xG differences as single-match observations, not proof of persistent finishing skill.
- Adds a player spotlight only when stored player-stat rows exist.
- Carries data warnings into the social-insight response.

## Content plan behavior

- Before kickoff: builds talking points from existing form, matchup signals, venue samples, and model probabilities where available.
- After the match: combines the result, match xG, player-stat evidence, and editorial follow-up angles.
- Long video: returns a set of grounded analysis segments, not an automatically narrated or fully scripted video.
- Shorts: returns concise hook ideas based on known fixture/result data.
- Caveats: records missing data and avoids inventing lineups, injuries, tactics, events, or player performances.

## Important data-source limitation

Analytics only reads persisted player-match statistics; it does not fabricate or fetch player statistics. The current Football-Data free API integration does not provide the detailed per-player match event/stat line required for reliable player analysis. A provider adapter/ingestion workflow that populates `PlayerMatchStats` is still needed for full production coverage. Until that is connected, the API returns an explicit missing-data warning and omits player-specific claims.

The impact score is a transparent editorial ranking heuristic, not a prediction, betting signal, or validated player-quality metric.
