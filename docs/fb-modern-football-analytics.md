# Modern football analytics for FB social content

## API

`GET /api/fb/analytics/matches/{matchId}/modern-report`

This read-only endpoint returns a structured report for preview and post-match content: match metadata, team-level advanced metrics, tactical comparisons, player contribution signals, a score/xG review and a social-content brief.

## Analytics currently supported

- **Team style/context:** possession, PPDA, field tilt, xG chain and buildup, set-piece xG, counterattack xG, deep completions, final-third entries, and shots inside/outside the box when persisted by the provider.
- **Player contribution:** goals, assists, xG, xA, shots on target, key passes, tackles, interceptions, duels and cards when player-match stats are available.
- **Post-match:** scoreline, xG, goals-minus-xG descriptive residual, and a caveated content narrative.
- **Editorial outputs:** evidence-backed hooks, suggested formats for Facebook, TikTok/Shorts, YouTube and carousels, plus explicit cautions.
- **Data quality:** missing metrics remain null; the response lists limitations instead of inventing values.

## Interpretation caveats

- PPDA is often interpreted as a pressing-intensity proxy, but its exact definition and coverage vary by provider.
- Field tilt is only comparable when providers use a consistent definition.
- Goals minus xG is descriptive and is not proof of luck or repeatable finishing ability.
- Player contribution score is a transparent heuristic for sorting content candidates, not an official rating and not suitable for cross-provider comparisons without normalization.
- The current persisted team stats entity does not include shots, shots on target, corners or card totals at team level. Those fields are returned null until the schema/provider mapper supplies them.
- This report does not currently infer injuries, predicted lineups, tactical formations, pressing triggers, player tracking or shot maps. Such claims require corresponding sourced data.
- Pre-match reports must not present match outcomes, post-match scores or same-match statistics as known facts. Consumers should select content sections according to fixture status.

## Recommended next provider-data work

1. Ensure each provider adapter maps the available advanced stats and player stats into the canonical entities.
2. Add explicit data provenance (provider, source timestamp and coverage) to the analytics payload before automated publishing.
3. Add sourced lineup/injury availability and event/shot-level datasets only where provider licensing and coverage permit.
4. Add integration tests against representative provider payloads and verify every metric's unit/definition.

## API usage

Call the endpoint for a match ID and use only populated fields. A missing value is unknown, not zero. The brief's editorial hooks are suggestions, not automatically publishable claims without source and status checks.
