# FB Data Coverage Audit

## Run

After the application starts against the database that was populated by `syncCoreData`, call:

```http
GET http://localhost:8188/api/fb/analytics/data-coverage-audit
```

The endpoint is read-only: it does not fetch provider data, modify rows, or delete duplicates. It audits league/season/team/match counts, finished-match score and xG coverage, standings by `TOTAL`/`HOME`/`AWAY`, provider-specific external-ID mappings, teams without fixtures, leagues without seasons, seasons without fixtures, and duplicate fixture signatures.

## How to interpret

- Top-level `status`: `CRITICAL` if a critical finding exists, otherwise `WARNING` if a warning exists, otherwise `OK`. Informational findings do not change the status.
- `totals`: global counts. `finishedMissingScores` must be zero before treating finished results as a reliable training label.
- `providers.mappingCoverage`: mapping completeness for the currently configured league/team/fixture providers. A missing mapping can be legitimate if the provider does not cover that entity; investigate before changing data.
- `seasons[].issues`: season-specific missing fixtures, missing finished scores, or missing/incomplete TOTAL standings.
- `findings[].context`: suggested investigation direction.

## Important caveats

- Duplicate fixtures are flagged by matching season, home team, away team, and kickoff time. This is a review signal, not an instruction to delete rows; legitimate same-team fixtures and provider corrections must be checked first.
- Missing xG is informational because a goals-only baseline can run without xG. Do not block all analytics solely on xG coverage.
- This endpoint reports persisted database coverage. It cannot prove that a provider offers every historical season or that a failed HTTP request was caused by plan restrictions. Compare the report with provider sync logs and configured competition/season values.
- The report should be run after bootstrap and after historical backfills; save the JSON output as the before/after comparison for subsequent ingestion fixes.
