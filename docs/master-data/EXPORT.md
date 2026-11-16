# Master Data Export and Initialization

The JSON initializer is a curated snapshot of MMO canonical master data. To populate it
from the data already ingested from providers, first run the normal provider catalogue,
season and team synchronization workflows. Then export the persisted canonical records
and provider identity mappings.

## Export current MMO state

```bash
curl -L -X GET http://localhost:8188/api/fb/master-data/export \
  -H "X-Ingestion-Admin-Key: your-secure-admin-key" \
  -o football-master-data.json
```

The endpoint exports every canonical league, season and team currently stored in MMO,
plus every external mapping row currently stored for all values of the `Provider` enum:
`SOFA_SCORE`, `UNDER_STATS`, `API_FOOTBALL`, `FOOTBALL_DATA` and
`FOOTBALL_DATA_UK`.

The export is a snapshot of **persisted mappings**, not a live crawl of provider catalogues.
A provider item that has not yet been ingested and reconciled to a canonical MMO entity
cannot appear as a canonical mapping in this file. Run provider discovery/synchronization
first, inspect unmatched records, and only then export. This prevents fuzzy name matching
or guessed external IDs from silently creating incorrect canonical identities.

## Preview and apply

Use the exported file to replace
`src/main/resources/master-data/football-master-data.json`, review the diff, then run
the existing initialization endpoint in `DRY_RUN` mode. Apply only after the report
contains no validation errors.

The export endpoint is protected by the same `X-Ingestion-Admin-Key` setting as
initialization. It returns a downloadable JSON attachment and does not modify database
records.
