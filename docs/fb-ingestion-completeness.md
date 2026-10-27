# Ingestion completeness: football-data.org free tier

## Scope and constraints

The core free-tier ingestion contract is league/competition metadata, season catalogue metadata exposed by the competition resource, teams, matches/fixtures and the provider's available standings. The configured competition list must stay within the provider's published free coverage. The API has a documented free-plan quota of 10 requests/minute; the shared client deliberately paces requests rather than parallelizing calls.

References:
- [Official free coverage](https://www.football-data.org/coverage)
- [Official API quickstart and resource catalogue](https://www.football-data.org/documentation/quickstart)
- [Official API policies and throttling](https://docs.football-data.org/general/v4/policies.html)

The provider's response is authoritative for what seasons are actually exposed. Do not fabricate a provider season mapping just because a year appears in `FOOTBALL_DATA_SEASONS`. The local application may create a canonical season row for a configured backfill attempt, but that row must not be interpreted as proof that the provider exposes that season.

## Required ingestion order

1. Fetch configured competitions and upsert league metadata plus real season IDs/dates from the competition season catalogue.
2. Ensure canonical season rows exist for configured backfill years, without inventing provider external-ID mappings for absent seasons.
3. Fetch teams by competition and season; upsert provider team IDs and canonical team mappings.
4. Fetch fixtures for each configured season; upsert by provider match ID, preserving existing scores/status when an incoming response omits them.
5. Fetch standings only where the provider returns them. Empty or restricted standings are not proof that the season has no table.
6. Run the existing data coverage audit and reconcile configured seasons against actual fixture counts, finished-score completeness, team mappings and standing types.

## Data required for a monetizable football content pipeline

### P0 — reliable core data

- Competition/league and season identity, including true provider season IDs where available.
- Teams and stable provider-to-canonical mappings.
- Fixtures with UTC kickoff, stage/matchday, status, venue and final scores.
- Current standings by TOTAL/HOME/AWAY where available.
- Per-resource sync outcome: fetched, inserted/updated, skipped, restricted, rate-limited and failed.
- Coverage checks that distinguish an empty response, a restricted season, a failed request and a season that genuinely has no fixtures.

### P1 — richer analysis, subject to provider entitlement

- Goals/scorers and match events.
- Player identity and season-level scoring/assist records.
- Lineups, player/team statistics, shots, possession and expected goals (xG).

Do not assume those fields are available from the free football-data.org plan. Keep them behind resource-specific provider capabilities and use the already configured SofaScore/other providers only where permitted and operationally reliable.

### P2 — context that improves pre-match content

- Confirmed and probable lineups, injuries/suspensions, and manager/news context.
- Source timestamp and provenance for every non-core fact.
- A freshness policy that prevents stale or missing upstream data from being presented as confirmed.

## Acceptance criteria for the ingest module

- Re-running ingestion is idempotent and does not duplicate canonical fixtures, teams, seasons or standings.
- Real provider season IDs are used whenever supplied.
- Restricted/unsupported seasons are visible as partial coverage, never silently reported as complete.
- A provider returning no rows does not cause existing persisted data to be deleted.
- A completed bootstrap is not treated as a data-quality pass until the coverage audit is checked.
- No additional scheduler is introduced; the existing job registry remains the scheduling source of truth.
