# Ingestion provider configuration

All provider configuration is bound under `app.ingestion.provider-config`, keyed by the `Provider` enum. Resource routing remains separate under `app.ingestion.providers`.

## Shared fields

Each provider can define:

- `enabled`: whether this provider can be selected for ingestion.
- `base-url`: API base URL.
- `api-key`: optional provider credential; provider clients decide whether it is required.
- `league-ids`: provider-native competition/tournament IDs.
- `seasons`: configured calendar years where the provider/resource supports year-based selection.
- `web-url`: optional browser entry point for providers such as SofaScore.
- `options`: provider-specific settings such as SofaScore's `tournament-region`.

Example:

```yaml
app:
  ingestion:
    providers:
      league: ${INGESTION_LEAGUE_PROVIDER:FOOTBALL_DATA}
      team: ${INGESTION_TEAM_PROVIDER:FOOTBALL_DATA}
      fixture: ${INGESTION_FIXTURE_PROVIDER:FOOTBALL_DATA}
      standings: ${INGESTION_STANDINGS_PROVIDER:FOOTBALL_DATA}
      odds: ${INGESTION_ODDS_PROVIDER:API_FOOTBALL}

    provider-config:
      FOOTBALL_DATA:
        enabled: ${FOOTBALL_DATA_ENABLED:true}
        base-url: ${FOOTBALL_DATA_BASE_URL:https://api.football-data.org/v4}
        api-key: ${FOOTBALL_DATA_API_KEY:}
        league-ids: ${FOOTBALL_DATA_COMPETITION_IDS:}
        seasons: ${FOOTBALL_DATA_SEASONS:}

      API_FOOTBALL:
        enabled: ${API_FOOTBALL_ENABLED:true}
        base-url: ${API_FOOTBALL_BASE_URL:https://v3.football.api-sports.io}
        api-key: ${API_FOOTBALL_API_KEY:}
        league-ids: ${API_FOOTBALL_LEAGUE_IDS:}
        seasons: ${API_FOOTBALL_SEASONS:}

      SOFA_SCORE:
        enabled: ${SOFA_SCORE_ENABLED:true}
        base-url: ${SOFA_SCORE_API_URL:${SOFASCORE_API_URL:https://www.sofascore.com/api/v1/}}
        web-url: ${SOFA_SCORE_WEB_URL:https://www.sofascore.com}
        league-ids: ${SOFASCORE_TOURNAMENT_IDS:}
        options:
          tournament-region: ${SOFASCORE_TOURNAMENT_REGION:EN}

      UNDER_STATS:
        enabled: ${UNDER_STATS_ENABLED:false}
        base-url: ${UNDER_STAT_BASE_URL:${UNDERSTAT_BASE_URL:https://understat.com}}
        league-ids: ${UNDER_STATS_LEAGUE_IDS:}
        seasons: ${UNDER_STATS_SEASONS:}
```

## Compatibility and behavior

- Existing clients keep using typed `AppProperties` accessors. These accessors resolve their values from the shared map and translate provider-specific names such as Football Data's `competition-ids` to the shared `league-ids` field.
- The old typed configuration fields remain as a source-compatibility fallback for code/tests that construct `AppProperties` directly. The application YAML no longer binds duplicate provider-specific configuration trees.
- SofaScore's `tournament-region` is represented in `options`; SofaScore season IDs continue to use external-ID mappings, not calendar years.
- Only `FOOTBALL_DATA` creates canonical season rows. Secondary providers can filter or map existing canonical seasons but must not synthesize canonical records.
- `enabled` does not imply that an API key is required. Each provider client retains its own authentication and request behavior.
- `app.ingestion.providers` selects a provider per resource; it is not the same thing as `provider-config`, which stores each provider's connection settings.
