# Phase 4 — Season-scoped standings

## Canonical row identity

A standings row is identified by:

`season_id + team_id + standing_type + group_key`

- `standing_type`: `TOTAL`, `HOME`, or `AWAY`.
- `group_name`: provider-facing display value; nullable for an ungrouped table.
- `group_key`: non-null normalized discriminator. Empty string means no group; otherwise uppercase trimmed group name.

The group dimension is separate from the table type. A provider may return a TOTAL table for each tournament group; these rows must not overwrite one another. HOME and AWAY remain separate from TOTAL.

## Sync behavior

- Standings sync enumerates every season mapped to the selected provider and owned by that provider under `DataSourcePolicy`, not only active seasons.
- A selected season is still executed through the Phase 3 season-scoped work unit.
- Empty/restricted standings responses are reported as `NOT_AVAILABLE`; existing rows are preserved.
- A future season with no table is reported as not started.
- Rows with inconsistent played/W-D-L or goal difference are rejected and counted.
- Missing canonical team mappings and duplicate rows are reported; the work unit is `PARTIAL`.
- A provider response with missing optional statistics preserves null; the importer does not fabricate zero.
- `last_synced_at` is refreshed on every successful upsert.

## Reconciliation rules

When all source values are present:
- `played = won + drawn + lost`
- `goal_difference = goals_for - goals_against`

Points are not recomputed because deductions and competition-specific rules can make a derived value incorrect.

## Database migration

For an existing MySQL database, back up `fb_league_standings` and run
`phase4-standings-group-uniqueness.sql` once. Hibernate schema update adds the new
`group_key` and composite unique index; the migration removes the legacy
`(season_id, team_id, standing_type)` index that otherwise blocks multiple groups.

For a fresh database, the JPA entity declares the composite unique key directly.

## Coverage report

The ingestion log emits one line per league-season with fetched, persisted, unmapped,
invalid and duplicate row counts, plus a final provider summary. A season returning no
standings is explicitly logged as `NOT_AVAILABLE`; the importer never deletes prior
rows because an empty provider response is not evidence that the previous table is invalid.
