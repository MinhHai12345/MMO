-- Apply after the application has added fb_league_standings.standing_type.
-- Existing rows are the overall table and are therefore backfilled as TOTAL.
UPDATE fb_league_standings
SET standing_type = 'TOTAL'
WHERE standing_type IS NULL OR standing_type = '';

-- Replace the old uniqueness rule (season + team) so each team can have
-- distinct TOTAL, HOME and AWAY rows within a season.
ALTER TABLE fb_league_standings
    DROP INDEX uk_standing_season_team;

ALTER TABLE fb_league_standings
    ADD CONSTRAINT uk_standing_season_team_type
        UNIQUE (season_id, team_id, standing_type);
