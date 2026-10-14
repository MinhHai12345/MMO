-- Run before deploying the unique constraints on provider external-ID mappings.
-- Review and reconcile duplicate rows manually; do not delete mappings blindly.

SELECT 'league' AS entity_type, provider, external_id, COUNT(*) AS duplicate_count
FROM fb_league_external_ids
GROUP BY provider, external_id
HAVING COUNT(*) > 1;

SELECT 'season' AS entity_type, provider, external_id, COUNT(*) AS duplicate_count
FROM fb_season_external_ids
GROUP BY provider, external_id
HAVING COUNT(*) > 1;

SELECT 'team' AS entity_type, provider, external_id, COUNT(*) AS duplicate_count
FROM fb_team_external_ids
GROUP BY provider, external_id
HAVING COUNT(*) > 1;

SELECT 'match' AS entity_type, provider, external_id, COUNT(*) AS duplicate_count
FROM fb_match_external_ids
GROUP BY provider, external_id
HAVING COUNT(*) > 1;
