# Current-season Match Statistics and xG pipeline

## Scope

This pipeline enriches canonical finished matches for seasons marked `is_current = true`. It uses the existing SofaScore crawler because the repository already has a statistics endpoint client, SofaScore external match-ID mappings, a Quartz/CronJob job, and xG persistence. It does not assume that the free API-Football or football-data.org plans expose these fields.

## Flow

1. `MatchStatisticsSyncJob` invokes `SofaScoreCrawlStrategy.storeMatchStatistics()`.
2. The repository selects finished matches in current seasons that have a SofaScore external ID and either lack a per-team statistics row or lack one/both xG values.
3. Candidates are ordered by oldest/null statistics attempt first, then newest kickoff, to avoid repeatedly selecting the same recent matches and starving older current-season matches.
4. The crawler fetches the match's `ALL` period statistics (falling back to the first non-empty period).
5. The strategy persists xG on `Match.homeXG/awayXG` and stores per-team statistics in `MatchAdvancedStats`.
6. Each per-team snapshot records `statisticsProvider` and `statisticsSyncedAt`.

## Fields persisted

- Expected goals: `Match.homeXG`, `Match.awayXG`.
- Existing advanced fields: possession, PPDA, field tilt, xG chain/build-up, deep completions, final-third entries, shots from/outside the box, set-piece xG and counter-attack xG.
- Standard match statistics when present: total shots, shots on target/off target, blocked shots, corners, fouls, yellow/red cards, offsides, total/accurate passes, pass accuracy, goalkeeper saves, big chances created/missed.

Provider values are optional. Missing fields remain null; they are never converted to zero.

## Retry and coverage behavior

- Only finished matches with a SofaScore mapping are eligible.
- A match is throttled by `statisticsLastAttemptAt` for 24 hours.
- Missing xG remains eligible for a later retry even if some standard statistics were saved.
- Provider responses that omit a statistic do not fabricate a value. Coverage reporting should distinguish missing provider data from an actual zero.
- The current-season filter depends on `Season.isCurrent` being maintained correctly by the existing season-sync/bootstrap process.

## Analytics

The existing `TeamFormFeatureServiceImpl` reads `Match.homeXG/awayXG` for historical xG For/Against features. The new standard statistics are persisted for subsequent analytics/content integration; this change does not claim that those fields are already used in pre-match predictions.

## Operational note

The source of truth for these statistics in this change is SofaScore. Do not call the same values API-Football values unless an API-Football statistics provider is implemented and validated separately. Check the terms and permitted use of third-party data before using crawler output commercially.
