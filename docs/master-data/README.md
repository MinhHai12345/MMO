# Canonical Football Master Data

Source file: `src/main/resources/master-data/football-master-data.json`

This file is a curated seed for canonical MMO identities. It is not provider crawl output.
It is deliberately not imported at application startup.

## Schema

- `leagues[].code`: stable MMO canonical key; also stored as `fb_leagues.slug`.
- `leagues[].name/country/logoUrl/active/popularityScore`: initial canonical metadata.
- `leagues[].seasons[]`: seasons owned by the league, keyed by `year`.
- `teams[]`: canonical teams are declared independently of seasons.
- `externalMappings`: map provider enum names to provider-side external IDs. Supported values are the MMO `Provider` enum names, e.g. `FOOTBALL_DATA`, `FOOTBALL_DATA_UK`, `API_FOOTBALL`, `SOFA_SCORE`, `UNDER_STATS`.

Example structure (IDs are placeholders and must be replaced with verified provider IDs):

```json
{
  "leagues": [
    {
      "code": "premier-league",
      "name": "Premier League",
      "country": "England",
      "logoUrl": null,
      "active": true,
      "popularityScore": 100,
      "externalMappings": {
        "FOOTBALL_DATA": "PL",
        "SOFA_SCORE": "17"
      },
      "seasons": [
        {
          "year": "2026/2027",
          "current": true,
          "active": true,
          "startDate": "2026-08-01",
          "endDate": "2027-05-31",
          "externalMappings": {
            "FOOTBALL_DATA": "2026",
            "SOFA_SCORE": "76986"
          }
        }
      ]
    }
  ],
  "teams": [
    {
      "code": "arsenal",
      "name": "Arsenal FC",
      "shortName": "Arsenal",
      "logoUrl": null,
      "active": true,
      "externalMappings": {
        "FOOTBALL_DATA": "57",
        "SOFA_SCORE": "42"
      }
    }
  ]
}
```

Do not copy placeholder IDs. Verify every provider ID against its provider catalogue first.
A canonical code must be stable and unique within its entity type. Existing canonical records
are matched by code (League slug / Team slug); matching by name alone is rejected to avoid
silently merging different entities.

## API

`POST /api/fb/master-data/initialize`

Header: `X-Ingestion-Admin-Key: <INGESTION_ADMIN_API_KEY>`

Preview (default, no writes):

```json
{ "mode": "DRY_RUN" }
```

Apply (explicit confirmation required):

```json
{ "mode": "APPLY", "confirm": true }
```

The API is disabled when `app.ingestion.operations.api-key` is blank.
Apply is transactional. Existing canonical metadata is not overwritten; existing
provider mappings are not reassigned. Mapping conflicts abort the transaction.

Read-only reconciliation:

`GET /api/fb/master-data/reconcile`

Header: `X-Ingestion-Admin-Key: <INGESTION_ADMIN_API_KEY>`

The reconciliation compares the curated JSON seed with the persisted database snapshot.
It reports missing canonical leagues, seasons, teams and provider mappings as errors,
and reports season date differences as warnings for manual review. Database-only records
and mappings are retained and are not treated as errors because the checked-in seed is not
a complete provider catalogue. The endpoint performs no writes and returns HTTP 409 when
required canonical records or mappings are missing or conflict with the seed.
