-- Manual MySQL migration: run once before deploying the standing-type code.
-- Existing rows represent the overall table, so default them to TOTAL.
ALTER TABLE fb_league_standings
    ADD COLUMN standing_type VARCHAR(16) NOT NULL DEFAULT 'TOTAL';

-- Replace the old uniqueness rule (season + team) so each team can have
-- distinct TOTAL, HOME and AWAY rows within a season.
ALTER TABLE fb_league_standings
    DROP INDEX uk_standing_season_team;

ALTER TABLE fb_league_standings
    ADD CONSTRAINT uk_standing_season_team_type
        UNIQUE (season_id, team_id, standing_type);
