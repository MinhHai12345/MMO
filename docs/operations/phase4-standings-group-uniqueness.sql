-- Phase 4: run once against an existing MySQL database during a maintenance window.
-- Hibernate ddl-auto=update adds group_key and the new composite unique index from LeagueStanding.
-- This migration removes the legacy key that prevented the same team from having rows in
-- multiple groups for one season/table type. Back up the table before running.

ALTER TABLE fb_league_standings
    ADD COLUMN group_key VARCHAR(100) NOT NULL DEFAULT '';

UPDATE fb_league_standings
SET group_key = UPPER(TRIM(group_name))
WHERE group_name IS NOT NULL AND TRIM(group_name) <> '';

ALTER TABLE fb_league_standings
    DROP INDEX uk_standing_season_team_type;
