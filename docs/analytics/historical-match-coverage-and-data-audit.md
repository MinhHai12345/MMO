# Historical Match Coverage Benchmark & MMO Data Audit

> Scope: Men's senior competitions only. This document is a read-only benchmark and audit guide for the canonical MMO football data model. It does not modify or repair stored data.

## 1. Benchmark definition

The benchmark below covers club seasons **2014/15 through 2025/26** and the FIFA World Cup editions **2014, 2018, 2022 and 2026**.

Count rules:

- Domestic leagues: count regular league fixtures. A completed 20-team double round-robin season has 380 fixtures; an 18-team season has 306.
- EFL Championship: show both **552 regular-season fixtures** and **557 total competition fixtures** for seasons through 2025/26. The extra five are the four two-legged semi-final matches plus the final. The 2026/27 play-off format changes to seven matches; that in-progress season is intentionally outside this historical baseline.
- Ligue 1: use the actual shortened/18-team season totals, not a constant 380.
- UEFA Champions League (UCL): count the main competition from the group/league phase through the final, **excluding qualifying rounds**. 2019/20 had 119 main-competition matches because the final stages were shortened to single-leg ties after the COVID-19 interruption. The 2024/25 format has 189 matches.
- FIFA World Cup: count final-tournament matches only. The 2026 edition has 104 matches.

These are competition fixture totals, not a promise that every provider exposes the same coverage or the same fields. MMO's canonical fixture count must be measured from `fb_matches`; provider IDs, snapshots and enrichment states are coverage dimensions, not additional fixtures.

## 2. Expected historical match counts

### 2.1 Club competitions by season

| Season | Premier League | Championship regular | Championship incl. play-offs | Bundesliga | LaLiga | Ligue 1 | Serie A | UCL main competition |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 2014/15 | 380 | 552 | 557 | 306 | 380 | 380 | 380 | 125 |
| 2015/16 | 380 | 552 | 557 | 306 | 380 | 380 | 380 | 125 |
| 2016/17 | 380 | 552 | 557 | 306 | 380 | 380 | 380 | 125 |
| 2017/18 | 380 | 552 | 557 | 306 | 380 | 380 | 380 | 125 |
| 2018/19 | 380 | 552 | 557 | 306 | 380 | 380 | 380 | 125 |
| 2019/20 | 380 | 552 | 557 | 306 | 380 | 279 | 380 | 119 |
| 2020/21 | 380 | 552 | 557 | 306 | 380 | 380 | 380 | 125 |
| 2021/22 | 380 | 552 | 557 | 306 | 380 | 380 | 380 | 125 |
| 2022/23 | 380 | 552 | 557 | 306 | 380 | 380 | 380 | 125 |
| 2023/24 | 380 | 552 | 557 | 306 | 380 | 306 | 380 | 125 |
| 2024/25 | 380 | 552 | 557 | 306 | 380 | 306 | 380 | 189 |
| 2025/26 | 380 | 552 | 557 | 306 | 380 | 306 | 380 | 189 |
| **12-season total** | **4,560** | **6,624** | **6,684** | **3,672** | **4,560** | **4,237** | **4,560** | **1,622** |

**Club competition total:** 29,895 fixtures when Championship play-offs are included; 30,035 when only its 552 regular-season fixtures are counted.

### 2.2 FIFA World Cup editions

| Edition | Expected matches |
|---|---:|
| 2014 | 64 |
| 2018 | 64 |
| 2022 | 64 |
| 2026 | 104 |
| **Total** | **296** |

**Grand total across the competitions above:** **30,191** (club competition total including Championship play-offs + World Cup editions). Excluding Championship play-offs: **30,131**.

> Important: these totals are an explicit benchmark scope. Do not compare them with a database-wide count that also includes domestic cups, continental competitions other than UCL, UCL qualifying rounds, youth/women's competitions, friendlies, reserve teams or duplicated provider records.

### 2.3 Source notes

- Premier League: official competition explanation states 20 clubs, home-and-away, 380 fixtures: https://www.premierleague.com/en/news/58905
- EFL: official competition page states 552 regular Championship fixtures; EFL approved seven Championship play-off fixtures from 2026/27: https://www.efl.com/about-the-efl/our-competitions/ and https://www.efl.com/news/2026/march/05/efl-statement--sky-bet-championship-play-off-format/
- Ligue 1 season archive (season-by-season totals, including 279 in 2019/20 and 306 from 2023/24): https://footballedgelab.com/ligue-1/seasons
- Bundesliga: 18 clubs × 17 opponents × home/away = 306: https://statz.ai/competitions/bundesliga
- LaLiga: 20 clubs × 19 opponents × home/away = 380: https://statz.ai/competitions/la-liga
- Serie A: 20 clubs × 19 opponents × home/away = 380: https://europeanleagues.com/wp-content/uploads/Domestic-Competitions-Season-202324-Overview-2.pdf
- UCL 2014/15 UEFA technical report records 125 matches: https://www.uefa.com/MultimediaFiles/Download/uefaorg/General/02/27/33/33/2273333_DOWNLOAD.pdf
- UCL 2024/25: UEFA's new 36-team league-phase and knockout format: https://www.uefa.com/uefachampionsleague/news/0290-1bae124dbd1a-4a9fc08cd25c-1000--2024-25-champions-league-teams-format-dates-draws-final/ . The 189 fixture total is the completed fixture inventory for that format.
- FIFA: official tournament history lists 64 matches for 2014/2018/2022 and 104 for 2026: https://www.fifa.com/en/tournaments/mens/worldcup/articles/quantos-jogos-copa-do-mundo-fifa and https://inside.fifa.com/tournament-organisation/fifa-world-cup-2026-by-the-numbers

The counts for round-robin leagues are calculated from the official competition format (number of clubs × opponents). Irregular seasons and tournament formats are called out above rather than extrapolated.

## 3. MMO data model inspected

The audit SQL is aligned with the entities currently present in the repository:

| Purpose | Canonical table | Important fields |
|---|---|---|
| Competition | `fb_leagues` | `id`, `name`, `slug`, `country`, `is_active` |
| Season | `fb_seasons` | `id`, `league_id`, `season_year`, `is_current`, `is_active` |
| Canonical fixture | `fb_matches` | `id`, `season_id`, `home_team_id`, `away_team_id`, `match_time`, `status`, scores, xG |
| Fixture provider identity | `fb_match_external_ids` | `match_id`, `provider`, `external_id` |
| Team provider identity | `fb_team_external_ids` | `team_id`, `provider`, `external_id` |
| League provider identity | `fb_league_external_ids` | `league_id`, `provider`, `external_id` |
| Season provider identity | `fb_season_external_ids` | `season_id`, `provider`, `external_id` |
| Match team statistics | `fb_match_advanced_stats` | `match_id`, `team_id`, xG, PPDA, shots, possession, passing |
| Provider statistics snapshots | `fb_match_statistics_source_snapshots` | `match_id`, `team_id`, `provider`, `snapshot_json`, `fetched_at` |
| Player match statistics | `fb_player_match_stats` | `match_id`, `player_id`, `team_id`, minutes, goals, xG/xA, passes |
| Provider/resource execution state | `fb_match_enrichment_states` | `match_id`, `provider`, `resource`, `status`, retry timestamps |
| Standings | `fb_league_standings` | `season_id`, `team_id`, `standing_type`, played, points, goals |

The canonical fixture is `Match`. A provider row in `fb_match_external_ids`, a snapshot, or an enrichment state must **never** be counted as another match. Multiple providers should converge on the same `fb_matches.id`.

## 4. Run the existing MMO audits first

Run against the DEV application. These endpoints are read-only reports:

```bash
curl -fsS http://localhost:8080/api/fb/analytics/data-coverage-audit
curl -fsS http://localhost:8080/api/fb/analytics/data-quality-validation
curl -fsS http://localhost:8080/api/fb/analytics/data-readiness
```

What they provide:

- `data-coverage-audit`: competition/season fixture counts, expected counts where implemented, fixture integrity, provider mapping coverage, enrichment state coverage, persisted team/player statistics and odds coverage.
- `data-quality-validation`: missing finished scores, negative or inconsistent values, duplicate canonical fixtures, invalid team/player statistics, standings inconsistencies and invalid odds values.
- `data-readiness`: score coverage, xG coverage, team-statistics coverage and snapshot freshness for active seasons.

The existing `DataCoverageAuditServiceImpl` calculates double-round-robin expectations from the number of teams represented in fixtures. For Championship it includes five play-off matches through 2025/26 and seven from 2026/27. It currently does not provide the full historical reference matrix in Section 2 for every competition; use the SQL below for that independent benchmark comparison.

## 5. Read-only MySQL audit script

Target: MySQL 8+. Run this against the DEV database using a read-only DB account. It uses the actual MMO table/column names. It does not insert, update or delete anything.

### 5.1 Compare MMO canonical fixture counts with the historical benchmark

This query returns expected seasons even when MMO has no matching league or season row. The competition-name matching is intentionally visible: if MMO uses a different display name, adjust the matching expression rather than silently treating the competition as absent.

```sql
WITH RECURSIVE years AS (
    SELECT 2014 AS start_year
    UNION ALL
    SELECT start_year + 1 FROM years WHERE start_year < 2025
),
club_competitions AS (
    SELECT 'PREMIER_LEAGUE' AS code
    UNION ALL SELECT 'CHAMPIONSHIP'
    UNION ALL SELECT 'BUNDESLIGA'
    UNION ALL SELECT 'LALIGA'
    UNION ALL SELECT 'LIGUE_1'
    UNION ALL SELECT 'SERIE_A'
    UNION ALL SELECT 'UCL'
),
club_benchmark AS (
    SELECT
        c.code,
        y.start_year,
        CASE c.code
            WHEN 'PREMIER_LEAGUE' THEN 380
            WHEN 'CHAMPIONSHIP' THEN 557
            WHEN 'BUNDESLIGA' THEN 306
            WHEN 'LALIGA' THEN 380
            WHEN 'LIGUE_1' THEN
                CASE
                    WHEN y.start_year = 2019 THEN 279
                    WHEN y.start_year >= 2023 THEN 306
                    ELSE 380
                END
            WHEN 'SERIE_A' THEN 380
            WHEN 'UCL' THEN
                CASE
                    WHEN y.start_year = 2019 THEN 119
                    WHEN y.start_year >= 2024 THEN 189
                    ELSE 125
                END
        END AS expected_matches
    FROM club_competitions c
    CROSS JOIN years y
),
world_cup_benchmark AS (
    SELECT 'FIFA_WORLD_CUP' AS code, 2014 AS start_year, 64 AS expected_matches
    UNION ALL SELECT 'FIFA_WORLD_CUP', 2018, 64
    UNION ALL SELECT 'FIFA_WORLD_CUP', 2022, 64
    UNION ALL SELECT 'FIFA_WORLD_CUP', 2026, 104
),
benchmark AS (
    SELECT * FROM club_benchmark
    UNION ALL
    SELECT * FROM world_cup_benchmark
),
mmo_leagues AS (
    SELECT
        l.id AS league_id,
        l.name AS league_name,
        CASE
            WHEN LOWER(l.name) LIKE '%premier league%' THEN 'PREMIER_LEAGUE'
            WHEN LOWER(l.name) LIKE '%championship%' THEN 'CHAMPIONSHIP'
            WHEN LOWER(l.name) LIKE '%bundesliga%' AND LOWER(l.name) NOT LIKE '%2.%' THEN 'BUNDESLIGA'
            WHEN LOWER(l.name) LIKE '%laliga%'
              OR LOWER(l.name) LIKE '%la liga%'
              OR LOWER(l.name) LIKE '%primera division%' THEN 'LALIGA'
            WHEN LOWER(l.name) LIKE '%ligue 1%' THEN 'LIGUE_1'
            WHEN LOWER(l.name) LIKE '%serie a%' THEN 'SERIE_A'
            WHEN LOWER(l.name) LIKE '%champions league%' THEN 'UCL'
            WHEN LOWER(l.name) LIKE '%world cup%' THEN 'FIFA_WORLD_CUP'
            ELSE NULL
        END AS code
    FROM fb_leagues l
),
season_counts AS (
    SELECT
        s.id AS season_id,
        s.league_id,
        CAST(LEFT(REGEXP_REPLACE(s.season_year, '[^0-9]', ''), 4) AS UNSIGNED) AS start_year,
        s.season_year,
        COUNT(m.id) AS stored_matches,
        SUM(m.status = 'FINISHED') AS finished_matches,
        SUM(m.status = 'FINISHED'
            AND m.home_score IS NOT NULL AND m.away_score IS NOT NULL) AS finished_with_scores,
        SUM(m.status = 'FINISHED'
            AND m.home_xg IS NOT NULL AND m.away_xg IS NOT NULL) AS finished_with_xg,
        SUM(m.status = 'SCHEDULED') AS scheduled_matches,
        SUM(m.status = 'LIVE') AS live_matches,
        SUM(m.status = 'POSTPONED') AS postponed_matches,
        SUM(m.status = 'CANCELLED') AS cancelled_matches,
        COUNT(DISTINCT m.home_team_id) + COUNT(DISTINCT m.away_team_id) AS home_away_team_count_sum
    FROM fb_seasons s
    LEFT JOIN fb_matches m ON m.season_id = s.id
    GROUP BY s.id, s.league_id, s.season_year
)
SELECT
    b.code AS benchmark_competition,
    ml.league_id,
    ml.league_name,
    sc.season_id,
    sc.season_year,
    b.start_year,
    b.expected_matches,
    COALESCE(sc.stored_matches, 0) AS stored_matches,
    COALESCE(sc.stored_matches, 0) - b.expected_matches AS match_difference,
    CASE
        WHEN ml.league_id IS NULL THEN 'MISSING_LEAGUE'
        WHEN sc.season_id IS NULL THEN 'MISSING_SEASON'
        WHEN sc.stored_matches < b.expected_matches THEN 'UNDER_COVERAGE'
        WHEN sc.stored_matches > b.expected_matches THEN 'OVER_COVERAGE_OR_SCOPE_MISMATCH'
        ELSE 'COUNT_MATCH'
    END AS count_status,
    COALESCE(sc.finished_matches, 0) AS finished_matches,
    COALESCE(sc.finished_with_scores, 0) AS finished_with_scores,
    COALESCE(sc.finished_with_xg, 0) AS finished_with_xg,
    COALESCE(sc.scheduled_matches, 0) AS scheduled_matches,
    COALESCE(sc.live_matches, 0) AS live_matches,
    COALESCE(sc.postponed_matches, 0) AS postponed_matches,
    COALESCE(sc.cancelled_matches, 0) AS cancelled_matches
FROM benchmark b
LEFT JOIN mmo_leagues ml ON ml.code = b.code
LEFT JOIN season_counts sc
    ON sc.league_id = ml.league_id
   AND sc.start_year = b.start_year
ORDER BY b.code, b.start_year, ml.league_id;
```

**How to read it:**

- `COUNT_MATCH`: stored canonical fixture count equals the benchmark.
- `UNDER_COVERAGE`: MMO is missing fixtures, or its competition scope/provider feed is incomplete.
- `OVER_COVERAGE_OR_SCOPE_MISMATCH`: inspect duplicate fixtures, play-offs, qualifying rounds, cup fixtures, season mapping or competition naming before deleting anything.
- `MISSING_LEAGUE` / `MISSING_SEASON`: competition catalogue or season catalogue is missing.
- A current season is not in this completed-season baseline. Do not classify its partial count as a historical failure.

### 5.2 Field completeness and statistics coverage by MMO season

This query counts unique canonical matches, then independently measures data stored against them. Advanced team statistics are expected for two sides; one row for one team is partial coverage, not a fully enriched match.

```sql
WITH match_side_stats AS (
    SELECT match_id, COUNT(DISTINCT team_id) AS teams_with_stats
    FROM fb_match_advanced_stats
    GROUP BY match_id
),
match_snapshots AS (
    SELECT
        match_id,
        COUNT(DISTINCT provider) AS providers_with_snapshot,
        COUNT(DISTINCT team_id) AS teams_with_snapshot,
        SUM(snapshot_json IS NULL OR TRIM(snapshot_json) = '') AS empty_snapshot_payloads,
        MAX(fetched_at) AS latest_snapshot_at
    FROM fb_match_statistics_source_snapshots
    GROUP BY match_id
),
match_provider_ids AS (
    SELECT
        match_id,
        COUNT(DISTINCT provider) AS mapped_provider_count,
        GROUP_CONCAT(DISTINCT provider ORDER BY provider SEPARATOR ',') AS mapped_providers
    FROM fb_match_external_ids
    GROUP BY match_id
),
match_players AS (
    SELECT match_id, COUNT(*) AS player_stat_rows
    FROM fb_player_match_stats
    GROUP BY match_id
)
SELECT
    l.name AS league_name,
    s.season_year,
    COUNT(m.id) AS canonical_matches,
    SUM(m.match_time IS NULL) AS missing_kickoff,
    SUM(m.home_team_id IS NULL OR m.away_team_id IS NULL) AS missing_canonical_team,
    SUM(m.home_team_id = m.away_team_id) AS same_home_away_team,
    SUM(m.status = 'FINISHED') AS finished_matches,
    SUM(m.status = 'FINISHED'
        AND (m.home_score IS NULL OR m.away_score IS NULL)) AS finished_missing_score,
    SUM(m.status = 'FINISHED'
        AND m.home_score IS NOT NULL AND m.away_score IS NOT NULL) AS finished_with_score,
    SUM(m.status = 'FINISHED'
        AND (m.home_half_time_score IS NULL OR m.away_half_time_score IS NULL)) AS finished_missing_half_time_score,
    SUM(m.status = 'FINISHED'
        AND (m.home_xg IS NULL OR m.away_xg IS NULL)) AS finished_missing_fixture_xg,
    SUM(m.round_info IS NULL OR TRIM(m.round_info) = '') AS missing_round_info,
    SUM(m.venue IS NULL OR TRIM(m.venue) = '') AS missing_venue,
    SUM(COALESCE(ms.teams_with_stats, 0) = 2) AS matches_with_both_team_stats,
    SUM(COALESCE(ms.teams_with_stats, 0) = 1) AS matches_with_one_team_stats,
    SUM(COALESCE(ms.teams_with_stats, 0) = 0) AS matches_without_team_stats,
    SUM(COALESCE(sn.providers_with_snapshot, 0) > 0) AS matches_with_source_snapshot,
    SUM(COALESCE(sn.teams_with_snapshot, 0) = 2) AS matches_with_two_snapshot_sides,
    SUM(COALESCE(sn.empty_snapshot_payloads, 0)) AS empty_snapshot_payload_rows,
    SUM(COALESCE(pi.mapped_provider_count, 0) = 0) AS matches_without_provider_match_id,
    SUM(COALESCE(pi.mapped_provider_count, 0) = 1) AS matches_mapped_to_one_provider,
    SUM(COALESCE(pi.mapped_provider_count, 0) >= 2) AS matches_mapped_to_multiple_providers,
    SUM(COALESCE(mp.player_stat_rows, 0) > 0) AS matches_with_player_stats,
    SUM(COALESCE(mp.player_stat_rows, 0)) AS player_stat_rows,
    MAX(sn.latest_snapshot_at) AS latest_snapshot_at
FROM fb_matches m
JOIN fb_seasons s ON s.id = m.season_id
JOIN fb_leagues l ON l.id = s.league_id
LEFT JOIN match_side_stats ms ON ms.match_id = m.id
LEFT JOIN match_snapshots sn ON sn.match_id = m.id
LEFT JOIN match_provider_ids pi ON pi.match_id = m.id
LEFT JOIN match_players mp ON mp.match_id = m.id
GROUP BY l.id, l.name, s.id, s.season_year
ORDER BY l.name, s.season_year DESC;
```

Notes:

- The model requires `match_time`, home team, away team and season on a canonical `Match`; those columns are non-null in JPA. The missing-field columns are defensive checks against legacy/schema drift.
- Do not require half-time score, venue, referee, attendance or player statistics for every fixture: provider availability and competition coverage differ. Report them as coverage ratios and define eligibility before turning them into hard failures.
- For finished matches, full-time scores should be complete. The existing data-quality validator already treats missing finished scores as critical.
- xG may be absent from some competitions/providers. Measure it separately from score coverage.
- `MatchAdvancedStats` is team-in-match data. Two distinct teams per fixture is the completeness target for a fully covered match.

### 5.3 Provider/resource ingestion state

This query distinguishes provider execution state from persisted data. A successful state does not prove that a complete payload was stored; compare it with Section 5.2.

```sql
SELECT
    l.name AS league_name,
    s.season_year,
    es.provider,
    es.resource,
    es.status,
    COUNT(*) AS state_rows,
    SUM(es.attempt_count) AS total_attempts,
    SUM(es.last_success_at IS NOT NULL) AS rows_with_success_timestamp,
    SUM(es.error_code IS NOT NULL) AS rows_with_error,
    MAX(es.last_attempt_at) AS latest_attempt_at,
    MAX(es.last_success_at) AS latest_success_at,
    MAX(es.next_retry_at) AS latest_next_retry_at
FROM fb_match_enrichment_states es
JOIN fb_matches m ON m.id = es.match_id
JOIN fb_seasons s ON s.id = m.season_id
JOIN fb_leagues l ON l.id = s.league_id
GROUP BY l.id, l.name, s.id, s.season_year, es.provider, es.resource, es.status
ORDER BY l.name, s.season_year DESC, es.provider, es.resource, es.status;
```

Status interpretation in MMO:

- `SUCCEEDED`: provider/resource attempt completed with data; verify the persisted resource separately.
- `EMPTY`: provider returned no usable data; not equivalent to successful data coverage.
- `SKIPPED`: deliberately not fetched; inspect eligibility and skip reason.
- `FAILED`: retry/failure path; inspect `error_code` and `error_message`.
- `PENDING` / `IN_PROGRESS`: work remains or may be stale; compare `last_attempt_at` with the retry/lease policy.

### 5.4 Provider identity coverage

A canonical match should normally have the configured fixture-provider mapping, and can additionally have mappings from other providers. Do not add the number of external IDs together and call that the match count.

```sql
SELECT
    provider,
    COUNT(*) AS external_id_rows,
    COUNT(DISTINCT match_id) AS canonical_matches_mapped,
    COUNT(DISTINCT external_id) AS distinct_external_ids,
    SUM(external_id IS NULL OR TRIM(external_id) = '') AS blank_external_ids
FROM fb_match_external_ids
GROUP BY provider
ORDER BY provider;

SELECT
    provider,
    COUNT(*) AS external_id_rows,
    COUNT(DISTINCT team_id) AS canonical_teams_mapped,
    COUNT(DISTINCT external_id) AS distinct_external_ids
FROM fb_team_external_ids
GROUP BY provider
ORDER BY provider;

SELECT
    provider,
    COUNT(*) AS external_id_rows,
    COUNT(DISTINCT league_id) AS canonical_leagues_mapped,
    COUNT(DISTINCT external_id) AS distinct_external_ids
FROM fb_league_external_ids
GROUP BY provider
ORDER BY provider;

SELECT
    provider,
    COUNT(*) AS external_id_rows,
    COUNT(DISTINCT season_id) AS canonical_seasons_mapped,
    COUNT(DISTINCT external_id) AS distinct_external_ids
FROM fb_season_external_ids
GROUP BY provider
ORDER BY provider;
```

### 5.5 Duplicate canonical fixtures

The canonical fixture identity used by the current data-quality audit is season + home team + away team + exact kickoff timestamp. This query shows groups and excess rows. It is diagnostic only; do not automatically delete them because rescheduled fixtures, timestamp corrections and provider reconciliation need review.

```sql
SELECT
    s.id AS season_id,
    l.name AS league_name,
    s.season_year,
    m.home_team_id,
    ht.name AS home_team,
    m.away_team_id,
    at.name AS away_team,
    m.match_time,
    COUNT(*) AS rows_in_group,
    GROUP_CONCAT(m.id ORDER BY m.id) AS match_ids
FROM fb_matches m
JOIN fb_seasons s ON s.id = m.season_id
JOIN fb_leagues l ON l.id = s.league_id
JOIN fb_teams ht ON ht.id = m.home_team_id
JOIN fb_teams at ON at.id = m.away_team_id
GROUP BY
    s.id, l.name, s.season_year,
    m.home_team_id, ht.name, m.away_team_id, at.name, m.match_time
HAVING COUNT(*) > 1
ORDER BY rows_in_group DESC, l.name, s.season_year;
```

### 5.6 Team/league/season mapping gaps

```sql
SELECT 'LEAGUE_WITHOUT_EXTERNAL_ID' AS issue, COUNT(*) AS affected_rows
FROM fb_leagues l
WHERE NOT EXISTS (
    SELECT 1 FROM fb_league_external_ids x WHERE x.league_id = l.id
)
UNION ALL
SELECT 'SEASON_WITHOUT_EXTERNAL_ID', COUNT(*)
FROM fb_seasons s
WHERE NOT EXISTS (
    SELECT 1 FROM fb_season_external_ids x WHERE x.season_id = s.id
)
UNION ALL
SELECT 'TEAM_WITHOUT_EXTERNAL_ID', COUNT(*)
FROM fb_teams t
WHERE NOT EXISTS (
    SELECT 1 FROM fb_team_external_ids x WHERE x.team_id = t.id
)
UNION ALL
SELECT 'MATCH_WITHOUT_EXTERNAL_ID', COUNT(*)
FROM fb_matches m
WHERE NOT EXISTS (
    SELECT 1 FROM fb_match_external_ids x WHERE x.match_id = m.id
);
```

A global no-mapping count is a useful first signal, not a final verdict: some canonical entities may legitimately not be mapped to every configured provider. For production acceptance, compare each table against the configured primary provider and the providers required for that competition/resource.

## 6. Recommended acceptance rules

Use these as an initial audit policy; tune thresholds by competition and resource rather than requiring every provider to have every field.

| Dimension | Acceptance condition |
|---|---|
| Competition catalogue | All benchmark competitions resolve to exactly one intended canonical league |
| Season catalogue | Every completed benchmark season exists and has a consistent `season_year` |
| Canonical fixtures | Stored count equals benchmark after competition-scope reconciliation |
| Fixture uniqueness | Zero duplicate canonical fixture groups, unless a reviewed exception is documented |
| Team identity | Home/away teams are canonical, distinct and mapped to the configured fixture provider where required |
| Fixture identity | No blank external IDs; provider + external ID maps to one canonical match |
| Finished score | 100% of `FINISHED` matches have both full-time scores |
| Match status | No unexplained status values; scheduled, live, postponed and cancelled counts are reconciled |
| xG | Report home/away pair coverage separately; do not treat null as zero |
| Team statistics | Report matches with 0, 1 and 2 distinct team-stat rows; measure provider snapshot provenance |
| Player statistics | Report match coverage and player row counts; eligibility differs by provider/competition |
| Freshness | Current-season snapshots meet configured freshness; historical snapshots are judged against the historical rebuild policy |
| Enrichment lifecycle | No unexplained stale `IN_PROGRESS`, `FAILED` or `PENDING` records for eligible fixtures |

## 7. Multi-provider reconciliation rules

1. **One fixture, one canonical Match.** Provider fixture IDs are aliases stored in `fb_match_external_ids`; they are not separate fixture rows.
2. **Resolve identity before merge.** Resolve competition, season, home team, away team and kickoff window before attaching an external ID. Do not merge on team display names alone.
3. **Do not use provider row counts as match counts.** A single match may have multiple external IDs, statistics snapshots and enrichment states.
4. **Keep provenance.** Use `fb_match_statistics_source_snapshots` to identify which provider supplied the raw team-stat payload and when it was fetched.
5. **Separate source status from data presence.** `SUCCEEDED`/ `EMPTY` / `SKIPPED` / `FAILED` describes an ingestion attempt. The canonical projection and snapshot tables show what is actually persisted.
6. **Do not overwrite a valid value with null.** Provider merge policy should preserve populated canonical fields unless an explicit correction policy allows replacement.
7. **Treat conflicts as findings.** If providers disagree on kickoff, score, team identity or xG, surface the conflict with provider IDs and timestamps. Do not silently choose whichever provider ran last.
8. **Reconcile scope before repairing counts.** UCL qualifying rounds, Championship play-offs, shortened seasons, neutral-site fixtures and competition aliases can explain count differences without implying missing data.

## 8. Suggested DEV run order

1. Run the three existing analytics endpoints in Section 4 and save their JSON outputs.
2. Run SQL 5.1 to identify missing/short/over-counted seasons.
3. For every mismatched season, run SQL 5.2 and 5.3 to identify whether the gap is fixture inventory, scores, xG, team statistics, player statistics, provider state or source freshness.
4. Run SQL 5.4 and 5.6 to inspect provider identity coverage.
5. Run SQL 5.5 and reconcile duplicate fixture groups before any repair.
6. Only after the findings are understood, create a targeted ingestion/reconciliation plan. Do not trigger a full rebuild merely because a statistics resource is missing.

## 9. Repository implementation references

- Canonical entities: `domain/league/entity/League.java`, `Season.java`, `domain/match/entity/Match.java`, `MatchExternalId.java`, `MatchAdvancedStats.java`, `MatchStatisticsSourceSnapshot.java`, `MatchEnrichmentState.java`.
- Audit services: `analytics/service/impl/DataCoverageAuditServiceImpl.java`, `DataQualityValidationServiceImpl.java`, `AnalyticsDataReadinessServiceImpl.java`.
- API: `analytics/controller/AnalyticsDataReadinessController.java`.
- Workflow gate: `workflow/application/service/DataReadinessGateService.java`.

This document is a baseline and diagnostic script. It does not claim that the DEV database has passed the audit; run it against the target database to obtain actual MMO counts.


## P0.7 match identity reconciliation

For the extended read-only audit covering exact duplicates, near-kickoff candidates, reversed home/away candidates, provider identity constraints and dependent-row inventory, see [P0.7 — Canonical Match Identity Audit](p0.7-match-identity-reconciliation.md). This is a diagnostic procedure only; it does not repair stored data.
