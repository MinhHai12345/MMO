# MMO Canonical Master Data Standard

## 1. Objective

MMO owns the canonical identity of football data. Provider IDs, names, slugs, and payload structures are source identities only. Every provider record must resolve to an MMO canonical entity before it is used by match ingestion or analytics.

This standard is designed for a clean database bootstrap. It does not instruct operators to delete a live database.

## 2. Canonical identity keys

| Entity | Canonical key | Database identity | Rule |
|---|---|---|---|
| League | `league.code` | `fb_leagues.slug` | Stable MMO-owned code; never derived from a provider ID |
| Season | `league.code + season.year` | `fb_seasons(league_id, season_year)` | A season belongs to exactly one canonical league |
| Team | `team.code` | `fb_teams.slug` | Stable MMO-owned code; never derived from team name or provider ID |
| Match | `match.canonicalKey` (to be specified against the current match model) | Existing match identity | Must resolve league, season, home team and away team to canonical IDs |

Canonical codes should be uppercase ASCII identifiers, stable over time, and independent of names. Example: `ENG_PREMIER_LEAGUE`, `2025/2026`, `ARSENAL`. Team identity is global; a club's participation in different competitions/seasons must not create duplicate canonical teams.

Names are display attributes, not identity keys. Do not auto-merge records on normalized name alone. Ambiguous cases require an explicit reviewed alias or mapping.

## 3. Canonical field standard

### League

| Canonical field | Meaning | Policy |
|---|---|---|
| code | MMO stable key | Required, curated |
| name | Display name | Required; curated canonical label |
| country | Country/territory | ISO 3166-1 alpha-2 recommended; normalize provider variants |
| logoUrl | Preferred logo | Provider candidate; validated URL and source tracked |
| active | Included in current ingestion | MMO operational decision |
| popularityScore | Internal ranking signal | MMO-derived, not copied blindly from provider |
| aliases | Historical/alternate names | Separate alias collection/table recommended |
| external identities | Provider IDs | One mapping per provider and canonical league |

### Season

| Canonical field | Meaning | Policy |
|---|---|---|
| leagueCode | Parent canonical league | Required |
| year | Stable season label | Required; examples `2025/2026`, `2025` |
| current | Current season flag | Derived by schedule/date policy; at most one current season per league |
| active | Include in scheduled ingestion | MMO operational decision |
| startDate/endDate | Actual season boundaries | Local calendar dates; null only when unknown; source and verification status tracked |
| external identities | Provider season IDs | One mapping per provider and canonical season |

Season label formats must remain source-compatible through explicit mapping. Do not silently convert a provider season integer into a different season label without a rule.

### Team

| Canonical field | Meaning | Policy |
|---|---|---|
| code | MMO stable key | Required, curated |
| name | Canonical display name | Required |
| shortName | Short display label | Optional |
| logoUrl | Preferred logo | Provider candidate; validated URL and source tracked |
| foundedYear | Foundation year | Optional; only accept valid years |
| stadiumName | Home ground name | Optional; preserve aliases/history if needed |
| stadiumCapacity | Capacity | Optional non-negative integer |
| active | Included in current ingestion | MMO operational decision |
| aliases | Former names, transliterations, abbreviations | Separate alias collection/table recommended |
| external identities | Provider team IDs | One mapping per provider and canonical team |

Team-league membership is time-dependent. Do not add a single permanent league ID to Team. Derive participation through season fixtures or a dedicated team-season membership model if required.

## 4. Provider identity mapping

Existing MMO tables already model identity mapping:

- `fb_league_external_ids`: `provider + external_id -> league_id`
- `fb_season_external_ids`: `provider + external_id -> season_id`
- `fb_team_external_ids`: `provider + external_id -> team_id`

The database unique constraints enforce:
1. A provider external ID maps to only one canonical entity.
2. A canonical entity has at most one external ID per provider.

League `identify` is a provider request parameter (code/name/slug/numeric key), not a canonical identity. Keep it separate from `external_id`.

Provider enum values are stable integration identifiers:
`SOFA_SCORE`, `UNDER_STATS`, `API_FOOTBALL`, `FOOTBALL_DATA`, `FOOTBALL_DATA_UK`.

Never use a provider's numeric ID as an MMO primary key or canonical code.

## 5. Mapping non-identity provider fields

Identity mapping alone is insufficient for fields such as league country/logo, team short name/logo/founded year/stadium, season dates, competition format, venue details, and other provider-enriched metadata.

Use a two-stage model:

1. **Provider observation (raw/source layer):** retain the provider, entity type, external ID, source field, raw value or payload, fetched timestamp, and source payload/version. This preserves what each provider actually supplied.
2. **Canonical projection (MMO domain layer):** map source fields into canonical fields through explicit field mappings and deterministic conflict policies.

Recommended field-mapping contract:

| Property | Purpose |
|---|---|
| provider | Source provider |
| entityType | LEAGUE, SEASON, TEAM, MATCH, VENUE, etc. |
| sourceField | Provider DTO/JSON field |
| canonicalField | MMO target field |
| transform | Named deterministic normalizer |
| priority | Field-specific source priority |
| validation | Required format/range |
| conflictPolicy | KEEP_CANONICAL, PREFER_PRIORITY, MANUAL_REVIEW |
| enabled | Mapping activation |

Recommended observation attributes: `provider`, `entity_type`, `external_id`, `source_field`, `raw_value`, `normalized_value`, `fetched_at`, `payload_hash`, `source_payload`.

Do not implement a generic reflection-based mapper that copies arbitrary provider JSON into entities. Use typed DTO-to-canonical mapping and a registry of explicit field rules.

### Default field conflict policy

| Field class | Default rule |
|---|---|
| Canonical code, canonical name, active flag | MMO curated value wins |
| External ID | Provider mapping only; never overwrite another provider's mapping |
| Country, short name, founded year, stadium | Accept only valid values; use configured field priority; preserve source observation |
| Logo/image URL | Validate URL; prefer configured source; never erase a valid URL with blank/null |
| Season start/end date | Use verified actual dates; preserve source and verification status; conflicts go to review |
| Popularity/ranking | Store as source-specific observation or calculate internally; do not mix scales |
| Match statistics (xG, PPDA, possession, shots, etc.) | Preserve provider, statistic definition, units, period and provenance; do not treat different definitions as interchangeable |

A source priority is field-specific. Do not declare one provider universally authoritative for every attribute.

## 6. Provider-to-canonical mapping examples

The following are mapping patterns, not actual external IDs:

| Source | Provider identity | MMO target |
|---|---|---|
| SofaScore tournament | tournament ID | League external mapping |
| SofaScore season | season ID | Season external mapping |
| SofaScore team | team ID | Team external mapping |
| Understat league | league identifier | League external mapping |
| Understat season | league identifier + season start year (adapter-defined ID) | Season external mapping |
| Understat team | team identifier/name as returned by adapter | Team external mapping after verification |
| Football-Data.org competition | competition code/ID | League external mapping |
| Football-Data.org season | source season identifier/year | Season external mapping |
| Football-Data.org team | team ID | Team external mapping |
| API-Football league | league ID | League external mapping |
| API-Football season | league ID + season year until a dedicated season ID is modeled | Season mapping only after adapter contract is explicit |
| API-Football team | team ID | Team external mapping |
| Football-Data.co.uk | competition code + season code | League and season mapping from curated source catalogue |
| Football-Data.co.uk team | normalized CSV team name | Resolve through reviewed alias/team mapping; never auto-create a team solely from a name |

Do not invent IDs for providers that do not expose a stable identifier. Where the source has only a code or a season label, define and test a deterministic provider key in that provider adapter.

## 7. Canonical master-data file

The checked-in JSON is the seed contract, not a dump of every source observation. Keep it focused on canonical entities and reviewed external mappings.

Example:

```json
{
  "schemaVersion": 2,
  "leagues": [
    {
      "code": "ENG_PREMIER_LEAGUE",
      "name": "Premier League",
      "country": "GB",
      "logoUrl": null,
      "active": true,
      "popularityScore": 0,
      "externalMappings": {
        "SOFA_SCORE": "17",
        "FOOTBALL_DATA": "PL",
        "API_FOOTBALL": "39"
      },
      "externalIdentifies": {
        "UNDER_STATS": "EPL"
      },
      "seasons": [
        {
          "year": "2025/2026",
          "current": true,
          "active": true,
          "startDate": "2025-08-15",
          "endDate": "2026-05-24",
          "externalMappings": {
            "SOFA_SCORE": "season-provider-id",
            "FOOTBALL_DATA": "2025",
            "API_FOOTBALL": "2025"
          }
        }
      ]
    }
  ],
  "teams": [
    {
      "code": "ARSENAL",
      "name": "Arsenal",
      "shortName": "Arsenal",
      "logoUrl": null,
      "active": true,
      "externalMappings": {
        "SOFA_SCORE": "team-provider-id",
        "FOOTBALL_DATA": "57",
        "API_FOOTBALL": "42"
      }
    }
  ]
}
```

Example IDs above are illustrative only and must not be copied into production seed data. The current Java `MasterDataFile` record does not yet include `schemaVersion`; implement a versioned DTO/migration before changing the JSON contract.

## 8. Clean bootstrap and full rebuild sequence

1. Freeze provider ingestion jobs and scheduled rebuild jobs.
2. Take and verify a database backup; record current schema/migration version.
3. Apply schema migrations to an empty target database. Do not manually drop selected tables while foreign keys or historical data remain.
4. Discover provider catalogues and collect source observations.
5. Review canonical league/season/team identities and resolve aliases/conflicts.
6. Generate and validate canonical JSON. Require unique codes, unique provider external IDs, valid dates, and complete mapping coverage for intended providers.
7. Run initialization DRY_RUN; inspect create counts, mapping counts, and validation errors.
8. Initialize canonical League, Season, Team and external identity mappings.
9. Fetch provider metadata and project approved fields into canonical records with provenance.
10. Ingest fixtures/results only after all referenced league, season and team identities resolve.
11. Ingest advanced match statistics and retain provider/statistic provenance.
12. Run full data quality checks before enabling scheduled ingestion.

A destructive database reset is a separate operational action and must require explicit environment confirmation. Never expose a reset endpoint without environment allow-list, admin authorization, backup verification, and a second explicit confirmation token.

## 9. Required implementation work against current MMO

Current entities already have most base canonical columns and three external-ID mapping tables. Current gaps to address before a full reset:

1. Add schema versioning to the master-data JSON and backward-compatible validation.
2. Add alias/identity review support for provider records that have no stable team identifier (notably CSV sources).
3. Add provider observation/provenance storage for non-identity fields. Current external-ID tables only map identity; they do not record which provider supplied a logo, stadium, date, or statistic.
4. Add an explicit field-mapping registry and field-specific source priority rules.
5. Add safe canonical projection services; provider adapters must not directly overwrite curated canonical fields.
6. Validate the current entity mappings and migration constraints against the fresh-database bootstrap path.
7. Test the entire bootstrap on a disposable empty MySQL database before any production reset.

## 10. Acceptance criteria

- A clean database can be initialized from canonical seed data without calling any provider.
- Every canonical league, season and team has a stable MMO code.
- Provider identities resolve deterministically to canonical IDs.
- No cross-provider name-only auto-merging occurs.
- Provider observations are traceable to source, external ID, fetch time and payload.
- Conflicting values are resolved by field-specific rules or sent for manual review.
- Match ingestion rejects or quarantines unresolved canonical references.
- Re-running initialization and provider discovery is idempotent.
- Full rebuild on a disposable database passes completeness and referential-integrity checks.
