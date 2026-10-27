# Active season ingestion

`fb_seasons.is_active` controls whether a season is included in detailed ingestion. A season is eligible only when both `fb_leagues.is_active = true` and `fb_seasons.is_active = true`.

- `is_current`: provider metadata describing the current season.
- `is_active`: operator selection controlling ingestion. These are intentionally separate.
- Newly discovered seasons are active by default only when the provider marks them current.
- Re-syncing catalogue metadata updates `is_current` and season dates but preserves the operator's `is_active` selection.
- Teams, fixtures and standings use the same active-season selection.

## Existing databases

After the schema update adds `fb_seasons.is_active`, run `docs/season-active-backfill.sql` once. It enables currently marked seasons to preserve the existing sync behavior. Historical seasons remain inactive until selected.

To activate a particular season:

```sql
UPDATE fb_seasons s
JOIN fb_leagues l ON l.id = s.league_id
SET s.is_active = TRUE
WHERE l.slug = 'bundesliga' AND s.year = '2023';
```

To deactivate it, set `s.is_active = FALSE` for the same league/year. The league must also remain active for its seasons to be fetched.
