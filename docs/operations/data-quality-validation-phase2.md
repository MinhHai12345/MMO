# Data Quality Validation (Phase 2)

## Purpose

Phase 1 reports whether expected data is present. Phase 2 validates whether persisted canonical football data is internally plausible and consistent before analytics consumes it.

Endpoint: `GET /api/fb/analytics/data-quality-validation`

The validator is read-only. It reports aggregate counts and does not load full entity collections, mutate rows, repair mappings, retry providers or delete data.

## Validation rules

| Area | Rule | Severity |
|---|---|---|
| Fixtures | Finished match missing either full-time score | CRITICAL |
| Fixtures | Negative full-time or half-time score | CRITICAL |
| Fixtures | Half-time score exceeds full-time score | WARNING |
| Fixtures | Negative xG or only one side has xG | WARNING |
| Fixtures | Same canonical team on both sides | CRITICAL |
| Fixtures | Duplicate season/home/away/kickoff signature | CRITICAL |
| Team statistics | Negative count metrics | CRITICAL |
| Team statistics | Possession or pass accuracy outside 0–100 | WARNING |
| Team statistics | Negative xG-family metrics | WARNING |
| Player statistics | Negative count metrics | CRITICAL |
| Player statistics | More than 130 minutes in one fixture | WARNING |
| Player statistics | Pass accuracy outside 0–100 or negative xG/xA | WARNING |
| Standings | Negative points, match counters or goal totals | CRITICAL |
| Standings | Played differs from W + D + L | CRITICAL |
| Standings | Goal difference differs from GF - GA | WARNING |
| Odds | Non-positive normalized or populated legacy odds | CRITICAL |

Points consistency is intentionally not enforced because historical competitions may have used different points systems.

## Status semantics

- `CRITICAL`: at least one critical validation rule has affected rows.
- `WARNING`: no critical findings, but at least one warning rule has affected rows.
- `OK`: no configured validation rule found affected rows.

Only rules with affected rows are returned. `criticalFindings` and `warningFindings` count triggered rules; each finding also exposes its affected row count.

## Analytics workflow integration

`GET /api/fb/analytics/workflow?from=...&to=...` now runs coverage audit, quality validation and readiness before backtests. A CRITICAL validation result blocks both backtests. WARNING findings are reported but do not automatically block them. Missing xG remains informational for the goals-only baseline.

## Boundaries

- No data repair, deletion, remapping, ingestion retry or checkpoint mutation.
- No assumption that provider execution success proves persisted data correctness.
- No automatic READY declaration for a season or model.
- Validation thresholds are conservative structural checks; domain-specific statistical outlier detection belongs in a later phase.
