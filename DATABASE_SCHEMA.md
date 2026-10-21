# FB domain schema reference

This document describes the current JPA model in `src/main/java/com/mmo/module/fb/domain/entity`. The entity annotations are the source of truth for schema generation; do not execute the legacy SQL snippets from earlier revisions as a substitute for Hibernate DDL validation.

The FB schema is being rebuilt from scratch during implementation. No backfill or legacy-column compatibility is required for this refactor.

## Identity and relationships

All internal relationships use the application's generated `id`. Provider IDs are stored only in provider mapping tables, never in the core `League`, `Season`, `Team`, or `Match` entities.

| Entity | Table | Identity / key rule |
|---|---|---|
| `League` | `fb_leagues` | Unique slug |
| `Season` | `fb_seasons` | Unique `league_id + year`; `year` is a domain season label |
| `Team` | `fb_teams` | Internal ID; provider IDs are mapped separately |
| `Match` | `fb_matches` | Internal ID; canonical season, home team, away team, kickoff, status and scores |
| `Player` | `fb_players` | Internal ID; provider IDs are mapped separately |
| `LeagueExternalId` | `fb_league_external_ids` | Unique `league_id + provider` and `provider + external_id` |
| `SeasonExternalId` | `fb_season_external_ids` | Unique `season_id + provider` and `provider + external_id` |
| `TeamExternalId` | `fb_team_external_ids` | Unique `team_id + provider` and `provider + external_id` |
| `MatchExternalId` | `fb_match_external_ids` | Unique `match_id + provider` and `provider + external_id` |
| `PlayerExternalId` | `fb_player_external_ids` | Unique `player_id + provider` and `provider + external_id` |

Provider identity is always the pair `(provider, external_id)`. The supported provider enum is defined in `com.mmo.module.fb.domain.enums.Provider`; do not hard-code a separate list in SQL documentation.

## Football data tables

| Table | Purpose | Important uniqueness / query indexes |
|---|---|---|
| `fb_league_standings` | Standings by season/team/type | Unique `season_id + team_id + standing_type`; indexed by season/type |
| `fb_match_advanced_stats` | Team advanced stats for a match | Unique `match_id + team_id` |
| `fb_player_match_stats` | Player stats for a match | Unique `player_id + match_id` |
| `fb_team_form` | Calculated team form | Unique `team_id + season_id + form_type` |
| `fb_h2h_records` | Directional head-to-head aggregates | Unique `home_team_id + away_team_id` |
| `fb_match_odds` | Current bookmaker market snapshot | Unique `match_id + bookmaker_id + market_name` |
| `fb_odds_history` | Historical odds observations | Indexed by `match_odds_id + recorded_at` |
| `fb_match_predictions` | Current prediction per match | Currently uses shared match identity through `@MapsId`; actual result is recorded only in prediction performance |
| `fb_prediction_performance` | Evaluation of a prediction | One-to-one with `fb_match_predictions`; stores actual result and scores once |
| `fb_match_insights` | Match insight snapshot | Shared match identity through `@MapsId` |
| `fb_match_narratives` | Match narrative snapshot | Shared match identity through `@MapsId` |

`StandingType.TOTAL`, `HOME`, and `AWAY` are separate records. A provider's HOME or AWAY row must never overwrite TOTAL.

Odds markets with different lines/handicaps must have distinct market identities before the model is expanded to support multiple lines for the same bookmaker and match. Odds values remain decimal types (`BigDecimal`).

## Content, subscription, and operations tables

| Table | Purpose |
|---|---|
| `fb_content_templates` | Reusable publication templates |
| `fb_social_posts` | Drafted and published content associated with a match |
| `fb_social_post_logs` | Publication/engagement history |
| `fb_content_queue` | Queued publication work |
| `fb_notification_queue` | Notification delivery queue |
| `fb_subscription_plans` | Subscription plan metadata |
| `fb_subscribers` | Subscriber and plan relationship |
| `fb_tip_transactions` | Subscriber delivery records |
| `fb_affiliate_tracking` | Affiliate click/conversion tracking |
| `fb_bookmakers` | Bookmaker metadata |
| `fb_crawler_health` | Crawler health snapshots |
| `fb_data_quality_metrics` | Data-quality measurements |

## Duplicate-field cleanup\n\n- League activation is represented only by `isActive`; the duplicate `active` flag was removed.\n- Match kickoff is represented only by `matchTime`; epoch timestamp duplication was removed from the canonical fixture model and Match entity.\n- Match round is represented only by `roundInfo`; the unused integer `round` field was removed.\n- LeagueStanding derives league through its required Season relationship; a second `league_id` is not stored.\n\n## Shared audit columns

Entities extending `AbstractEntity` inherit an identity primary key plus `created_by`, `created_date`, `last_modified_by`, and `last_modified_date`. The ID primary key already guarantees uniqueness; no additional unique constraint is required on the ID column.

## Clean schema verification

1. Start with an empty database.
2. Run the application with the configured Hibernate DDL mode and inspect the generated schema.
3. Verify all provider mapping unique constraints are present.
4. Run `./mvnw test`.
5. Run `syncCoreData()` and confirm league/season mappings are persisted before teams, and teams/seasons before fixtures.
6. Confirm fixture upserts are idempotent when the same provider fixture is ingested more than once.

Do not merge based solely on this document: entity metadata, repository query validation, integration tests, and the generated schema must agree.
