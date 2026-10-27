# Player-match statistics enrichment

## Purpose
The existing analytics API supports player spotlights, but football-data.org does not provide the detailed per-player match line required for reliable player claims. This enrichment uses the existing SofaScore match-ID mappings to retrieve player lineups/statistics for finished, scored matches.

## Behavior
- Uses the canonical match's SofaScore external ID; never guesses IDs from team names or kickoff times.
- Upserts player identity using SofaScore player external IDs and records the team represented in that specific match.
- Persists only returned lineup rows. Missing provider fields remain null instead of being invented.
- Processes at most 20 matches per existing statistics-job run and retries matches with missing player data no more often than every 24 hours.
- Uses a separate player-statistics attempt timestamp so player data retries do not block team-level statistics/xG synchronization.
- Adds no scheduler; the existing MatchStatisticsSyncJob invokes this enrichment.

## Limits and operational requirements
- The upstream lineups endpoint may omit player statistics for some competitions/matches; absent data must remain absent from player analytics.
- Live response shape and field aliases must be validated against the configured SofaScore endpoint before production use.
- Confirm provider terms and permitted commercial use before deploying crawler-derived data. Do not present missing lineup/player statistics as facts.
