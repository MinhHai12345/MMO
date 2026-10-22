# FB Data Coverage Audit and Fixture Integrity

## Run the audit

After the application starts against the populated database, call:

```http
GET http://localhost:8188/api/fb/analytics/data-coverage-audit
```

The endpoint is read-only. Global totals describe all persisted data. The per-season detail and season-level fixture/standing findings are scoped to `app.football-data.seasons`; when that list is empty, only seasons marked current are detailed. It audits league/season/team/match counts, finished score and xG coverage, standings by `TOTAL`/`HOME`/`AWAY`, provider-specific external-ID mappings, seasons without fixtures, exact duplicate signatures, and repeated home/away pairings.

## Historical backfill workflow

1. Configure the intended start years in `FOOTBALL_DATA_SEASONS`. For the current FB scope, use `2023,2024,2025,2026`. These are season start years, not calendar-year coverage.
2. Confirm `FOOTBALL_DATA_COMPETITION_IDS` contains the intended competition IDs and `FOOTBALL_DATA_API_KEY` is valid.
3. Trigger the existing `FootballDataBootstrapJob` through the application's CronJob controls (or run the same `FootballDataBootstrapService.syncCoreData()` operation). It syncs leagues/seasons, teams, all fixtures returned for configured seasons, and standings. Fixture persistence upserts by provider external ID, so rerunning is intended to be idempotent.
4. Review application logs for per-competition/season fixture counts, `403` restricted resources, rate-limit interruptions, skipped unmapped teams, and failed persistence.
5. Re-run the audit and compare only the configured seasons' `matches`, `expectedMatches`, `matchCountDifference`, `repeatedHomeAwayPairGroups`, and `fixtureIntegrityStatus`. Unconfigured historical seasons are not treated as backfill targets.

The client paces football-data.org requests conservatively, so historical backfill can take several minutes. Do not assume a zero-fixture response means a genuinely empty season: it can also mean the provider response was restricted or unavailable. The sync log is required to distinguish those cases.

## Fixture completeness and format assumptions

For domestic double round-robin leagues, the regular-season expectation is `teamCount * (teamCount - 1)`:
- Premier League, Primera Division and Serie A: 20 teams, 380 regular-season matches.
- Bundesliga: 18 teams, 306 regular-season matches.
- Championship: 24 teams, 552 regular-season matches **plus playoffs**.

The Championship totals of 557 matches in seasons starting 2023, 2024, and 2025 are **not by themselves evidence of duplicate fixtures**. The standard format has 552 league fixtures plus four semi-final legs and one final (5 playoff matches). The EFL has announced a change for 2026/27 to a seven-match playoff format; for a 24-team Championship season starting in 2026, the expected full competition total is 559. See the [EFL's 2026 playoff format announcement](https://www.efl.com/news/2026/march/05/efl-statement--sky-bet-championship-play-off-format/).

The audit therefore uses a season-aware expected count for the Championship and does **not** apply round-robin expectations to Champions League or other knockout/cup formats. A historical season below expected count is reported as `INCOMPLETE_FIXTURES`; above expected is `EXCESS_FIXTURES`. Repeated home/away pairs are a separate diagnostic, but a repeat is not automatically a duplicate because knockout ties and replay formats may legitimately repeat opponents.

### How to investigate the Championship records

For the 557-match seasons, first confirm the 5 playoff fixtures and their stages/round metadata. Only treat the remaining count or pairings as anomalous after separating regular-season fixtures from playoffs. Inspect provider external IDs and source match IDs before changing any records. Never delete rows automatically.

## How to interpret the response

- Top-level `status`: `CRITICAL` if a critical finding exists, otherwise `WARNING` if a warning exists, otherwise `OK`. Informational findings do not change status.
- `totals.finishedMissingScores` must be zero before finished results are reliable training labels.
- `providers.mappingCoverage` is mapping completeness for currently configured providers. A missing mapping can be legitimate if a provider does not cover that entity; investigate before changing data.
- `seasons[].issues` contains season-specific warnings, including fixture-count anomalies.
- Missing xG is informational because a goals-only baseline can run without xG.

## Important caveats

- Expected counts rely on known competition formats and configured league names; they are not universal for cups, split leagues, playoffs, or formats that change.
- The audit describes persisted database coverage. It cannot prove that a provider offers a given historical season or that a failed request was caused by subscription restrictions.
- Save audit JSON before and after each backfill to compare coverage and confirm no unexpected records were introduced.
