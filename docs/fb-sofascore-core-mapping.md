# SofaScore core external-ID mapping

## Purpose
football-data.org remains the canonical source for core league, season, team and fixture records. SofaScore attaches provider-specific IDs to those existing records so SofaScore statistics can resolve the same entities.

## Mapping rules
- League: match an existing active canonical league by normalized exact name; country is used to disambiguate. Ambiguous or unmatched tournaments are skipped.
- Season: match an existing season by canonical league and start year. SofaScore does not create missing seasons.
- Team: match an existing team by normalized exact name/short name. Ambiguous or unmatched teams are skipped.
- Fixture: match by canonical season, home team, away team and kickoff within +/- 6 hours. Ambiguous or unmatched fixtures are skipped.
- Provider-specific uniqueness is enforced by ExternalIdMappingService; conflicting IDs are never reassigned.
- SofaScore mapping does not overwrite canonical football-data scores, status, kickoff, names or metadata.
- Team and fixture ingestion iterates all seasons already mapped to SofaScore, not only the current season.

## Operational sequence
The existing MatchStatisticsSyncJob invokes the SofaScore bootstrap in order: tournament/season mappings, team mappings, fixture mappings, then match-statistics enrichment. No scheduler is added.

## Verification after deployment
Compare counts in fb_league_external_ids, fb_season_external_ids, fb_team_external_ids and fb_match_external_ids for provider='SOFA_SCORE'. Check logs for skipped unmatched/ambiguous identities. If names differ, add a reviewed alias/crosswalk rather than automatically creating duplicate core entities.
