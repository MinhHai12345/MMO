-- Run once after the application has added fb_seasons.is_active (for example via ddl-auto=update).
-- Keep existing current-season ingestion working; historical seasons remain inactive until selected.
UPDATE fb_seasons
SET is_active = TRUE
WHERE is_current = TRUE;
