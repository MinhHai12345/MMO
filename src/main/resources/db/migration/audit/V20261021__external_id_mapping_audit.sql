-- READ-ONLY audit for provider-aware external ID mapping migration.
-- Run against a backup/staging database first. This script intentionally performs
-- no INSERT/UPDATE/DELETE/ALTER operations.
--
-- IMPORTANT:
--  * fb_leagues.external_id and fb_teams.external_id do not store provider metadata.
--  * fb_seasons.external_id is currently populated with the season year in ingestion,
--    so it must NOT be treated as a provider external ID.
--  * fb_matches.external_id may be legacy/provider-prefixed. Do not infer a provider
--    from it without reviewing the actual values and ingestion history.

-- 1) Legacy ID coverage. Counts are diagnostic only; they do not prove provider identity.
SELECT 'league' AS entity_type,
       COUNT(*) AS total_rows,
       SUM(external_id IS NOT NULL AND TRIM(external_id) <> '') AS rows_with_legacy_external_id
FROM fb_leagues
UNION ALL
SELECT 'season', COUNT(*),
       SUM(external_id IS NOT NULL AND TRIM(external_id) <> '')
FROM fb_seasons
UNION ALL
SELECT 'team', COUNT(*),
       SUM(external_id IS NOT NULL AND TRIM(external_id) <> '')
FROM fb_teams
UNION ALL
SELECT 'match', COUNT(*),
       SUM(external_id IS NOT NULL AND TRIM(external_id) <> '')
FROM fb_matches;

-- 2) Mapping coverage by provider.
SELECT 'league' AS entity_type, provider, COUNT(*) AS mapping_rows,
       COUNT(DISTINCT league_id) AS mapped_entities,
       COUNT(DISTINCT external_id) AS distinct_external_ids
FROM fb_league_external_ids
GROUP BY provider
UNION ALL
SELECT 'season', provider, COUNT(*), COUNT(DISTINCT season_id), COUNT(DISTINCT external_id)
FROM fb_season_external_ids
GROUP BY provider
UNION ALL
SELECT 'team', provider, COUNT(*), COUNT(DISTINCT team_id), COUNT(DISTINCT external_id)
FROM fb_team_external_ids
GROUP BY provider
UNION ALL
SELECT 'match', provider, COUNT(*), COUNT(DISTINCT match_id), COUNT(DISTINCT external_id)
FROM fb_match_external_ids
GROUP BY provider
ORDER BY entity_type, provider;

-- 3) Legacy IDs with no mapping at all for the entity.
-- These rows need provider/source provenance before any backfill is considered.
SELECT 'league' AS entity_type, l.id AS entity_id, l.external_id AS legacy_external_id
FROM fb_leagues l
WHERE l.external_id IS NOT NULL AND TRIM(l.external_id) <> ''
  AND NOT EXISTS (SELECT 1 FROM fb_league_external_ids x WHERE x.league_id = l.id)
UNION ALL
SELECT 'season', s.id, s.external_id
FROM fb_seasons s
WHERE s.external_id IS NOT NULL AND TRIM(s.external_id) <> ''
  AND NOT EXISTS (SELECT 1 FROM fb_season_external_ids x WHERE x.season_id = s.id)
UNION ALL
SELECT 'team', t.id, t.external_id
FROM fb_teams t
WHERE t.external_id IS NOT NULL AND TRIM(t.external_id) <> ''
  AND NOT EXISTS (SELECT 1 FROM fb_team_external_ids x WHERE x.team_id = t.id)
UNION ALL
SELECT 'match', m.id, m.external_id
FROM fb_matches m
WHERE m.external_id IS NOT NULL AND TRIM(m.external_id) <> ''
  AND NOT EXISTS (SELECT 1 FROM fb_match_external_ids x WHERE x.match_id = m.id)
ORDER BY entity_type, entity_id;

-- 4) Duplicate provider + external ID pairs (should be prevented by unique constraints).
SELECT 'league' AS entity_type, provider, external_id, COUNT(*) AS row_count
FROM fb_league_external_ids
GROUP BY provider, external_id HAVING COUNT(*) > 1
UNION ALL
SELECT 'season', provider, external_id, COUNT(*)
FROM fb_season_external_ids
GROUP BY provider, external_id HAVING COUNT(*) > 1
UNION ALL
SELECT 'team', provider, external_id, COUNT(*)
FROM fb_team_external_ids
GROUP BY provider, external_id HAVING COUNT(*) > 1
UNION ALL
SELECT 'match', provider, external_id, COUNT(*)
FROM fb_match_external_ids
GROUP BY provider, external_id HAVING COUNT(*) > 1
ORDER BY entity_type, provider, external_id;

-- 5) More than one provider mapping for the same entity/provider pair
-- (should be prevented by unique constraints).
SELECT 'league' AS entity_type, league_id AS entity_id, provider, COUNT(*) AS row_count
FROM fb_league_external_ids
GROUP BY league_id, provider HAVING COUNT(*) > 1
UNION ALL
SELECT 'season', season_id, provider, COUNT(*)
FROM fb_season_external_ids
GROUP BY season_id, provider HAVING COUNT(*) > 1
UNION ALL
SELECT 'team', team_id, provider, COUNT(*)
FROM fb_team_external_ids
GROUP BY team_id, provider HAVING COUNT(*) > 1
UNION ALL
SELECT 'match', match_id, provider, COUNT(*)
FROM fb_match_external_ids
GROUP BY match_id, provider HAVING COUNT(*) > 1
ORDER BY entity_type, entity_id, provider;

-- 6) Potentially orphaned mappings (foreign keys may or may not be enabled in DB).
SELECT 'league' AS entity_type, x.id AS mapping_id, x.league_id AS entity_id
FROM fb_league_external_ids x LEFT JOIN fb_leagues e ON e.id = x.league_id
WHERE e.id IS NULL
UNION ALL
SELECT 'season', x.id, x.season_id
FROM fb_season_external_ids x LEFT JOIN fb_seasons e ON e.id = x.season_id
WHERE e.id IS NULL
UNION ALL
SELECT 'team', x.id, x.team_id
FROM fb_team_external_ids x LEFT JOIN fb_teams e ON e.id = x.team_id
WHERE e.id IS NULL
UNION ALL
SELECT 'match', x.id, x.match_id
FROM fb_match_external_ids x LEFT JOIN fb_matches e ON e.id = x.match_id
WHERE e.id IS NULL
ORDER BY entity_type, mapping_id;
