# FB Data Coverage Audit and Fixture Integrity

## Run the audit

After the application starts against the populated database, call:

```http
GET http://localhost:8188/api/fb/analytics/data-coverage-audit
```

The endpoint is read-only. It audits league/season/team/match counts, finished score and xG coverage, standings by `TOTAL`/`HOME`/`AWAY`, provider-specific external-ID mappings, seasons without fixtures, and duplicate fixture signatures.

## Historical backfill workflow

1. Configure the desired start years in `FOOTBALL_DATA_SEASONS`, for example `2021,2022,2023,2024,2025,2026`. This is a list of season start years, not calendar-year coverage.
2. Confirm `FOOTBALL_DATA_COMPETITION_IDS` contains the intended competition IDs and `FOOTBALL_DATA_API_KEY` is valid.
3. Trigger the existing `FootballDataBootstrapJob` through the application's CronJob controls (or run the same `FootballDataBootstrapService.syncCoreData()` operation). It syncs leagues/seasons, teams, all fixtures returned for configured seasons, and standings. Fixture persistence upserts by provider external ID, so rerunning is intended to be idempotent.
4. Review application logs for per-competition/season fixture counts, `403` restricted resources, rate-limit interruptions, skipped unmapped teams, and failed persistence.
5. Re-run the audit and compare each season's `matches`, `expectedMatches`, `matchCountDifference`, `repeatedHomeAwayPairGroups`, and `fixtureIntegrityStatus`.

The Free API quota is paced conservatively by the shared client, so historical backfill can take several minutes. Do not assume a zero-fixture response means a genuinely empty season: it can also mean the provider response was restricted or unavailable. The sync log is required to distinguish those cases.

## Fixture completeness and format assumptions

For the supported domestic double round-robin leagues, expected fixtures are calculated as `teamCount * (teamCount - 1)`:
- Premier League, Primera Division and Serie A: 20 teams, 380 matches.
- Bundesliga: 18 teams, 306 matches.
- Championship: 24 teams, 552 matches.

The audit deliberately does **not** apply this formula to Champions League or other knockout/cup formats. For a historical season, fewer than expected fixtures is reported as `INCOMPLETE_FIXTURES`; more than expected is reported as `EXCESS_FIXTURES`. Repeated home/away pairings are separately reported. These are diagnostic signals; do not delete rows automatically.

### Championship anomaly from the current audit

The reported 2023/2024/2025 Championship seasons each contain 557 matches, five above the 552-match double round-robin expectation. The new audit compares fixture count to the expected count and flags repeated home/away pairs regardless of kickoff time, which the old exact-kickoff duplicate signature could miss.

Investigate those five excess records by grouping on season, home team, and away team; then inspect provider external IDs and source match IDs. A legitimate rescheduled match should update the same provider fixture mapping rather than create another canonical match. Do not delete anything until the external IDs and source records confirm the duplicate.

## How to interpret the response

- Top-level `status`: `CRITICAL` if a critical finding exists, otherwise `WARNING` if a warning exists, otherwise `OK`. Informational findings do not change status.
- `totals.finishedMissingScores` must be zero before finished results are reliable training labels.
- `providers.mappingCoverage` is mapping completeness for currently configured providers. A missing mapping can be legitimate if a provider does not cover that entity; investigate before changing data.
- `seasons[].issues` contains season-specific warnings, including fixture-count anomalies.
- Missing xG is informational because a goals-only baseline can run without xG.

## Important caveats

- Expected fixture counts rely on known domestic league formats and configured league names; this check is not a universal rule for cups, split leagues, playoffs, or competitions whose format changed.
- The audit describes persisted database coverage. It cannot prove that a provider offers a given historical season or that a failed request was caused by subscription restrictions.
- Save audit JSON before and after each backfill to compare data coverage and confirm that no unexpected records were introduced.
