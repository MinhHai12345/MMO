# FB social-insight data integrity

## Current standings

`GET /api/fb/analytics/matches/{matchId}/social-insight` exposes the persisted `TOTAL` standings row only for a future match whose status is `SCHEDULED`.

For live or historical matches, the current standings row is not a reliable point-in-time snapshot for that match's kickoff. The endpoint therefore omits `leaguePosition`, `points`, and `standingsForm` and adds a warning rather than presenting current values as historical facts.

Before exposing a standings row, the service checks internal invariants when the relevant fields are present:

- points = 3 × won + drawn
- played = won + drawn + lost
- goal difference = goals for − goals against

An inconsistent row is omitted and a data warning is returned. These checks catch internal contradictions; they do not independently verify that the provider returned the correct competition, season, or latest table.

## Venue fallback

The response uses the fixture's venue first. If it is blank, the service falls back to the home team's persisted stadium name. If neither is available, `venue` remains null and the response contains a warning.

The fallback is stadium metadata, not proof that the venue was confirmed for the individual fixture.

## Limitations

- Standings are currently stored as a single latest row per season/team/type, not as historical snapshots. Accurate standings-as-of-kickoff for past matches requires persisted snapshots or reconstruction from results and competition-specific rules.
- The current consistency checks do not verify rank ordering, head-to-head tie-breaks, group-stage rules, deductions, or provider freshness.
- This change does not modify ingestion data or database schema.
