# Ingestion coverage audit (Phase 1)

The read-only endpoint `GET /api/fb/analytics/data-coverage-audit` audits persisted coverage by active league and season.

## Coverage dimensions

- Core entities and provider mappings: leagues, seasons, teams and fixtures.
- Fixture integrity: finished scores, xG availability, standings shape, duplicate fixture signatures and league-format fixture-count checks.
- Enrichment execution state: counts grouped by `MatchProvider + MatchEnrichmentResource + MatchEnrichmentStatus`.
- Persisted enrichment data: team statistics, player statistics, normalized odds quotes and legacy grouped odds.

## Interpreting enrichment coverage

Execution state and persisted data are reported separately. A `SUCCEEDED` state is not proof that the expected rows exist.

For `TEAM_STATISTICS` and `PLAYER_STATISTICS`, the eligible denominator is finished fixtures with both scores. For `ODDS`, the eligible denominator is all stored fixtures in the season. State coverage is the number of tracked match/provider/resource rows divided by that eligible denominator; persisted-resource coverage is distinct matches with rows divided by the same denominator.

`EMPTY`, `SKIPPED`, `FAILED`, `PENDING` and `IN_PROGRESS` are retained as separate counts. They are not collapsed into success. Missing state is calculated as eligible fixtures minus tracked state rows and is floored at zero.

Player statistics do not currently carry provider provenance on each persisted row, so the persisted player-statistics count is an aggregate by season; provider attribution remains available in the enrichment-state breakdown.

The audit is diagnostic and read-only. It does not repair, delete, re-map, retry or mutate ingestion data. It does not declare a season globally READY; that decision belongs to the later readiness-evaluation phase.
