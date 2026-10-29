# Provider league and team standardization

Provider league/team mappers translate provider DTOs into the shared `ProviderLeague` and
`ProviderTeam` contracts. They standardize display values, but do not decide cross-provider identity.

## Rules

- `externalId` remains the provider's stable ID; do not replace it with a name or display slug.
- League names use `MmoProviderDataStandardizer` for shared MMO display conventions and known aliases
  (currently including `Primera Division` / `La Liga` -> `LaLiga`).
- Team names are trimmed and internal whitespace is collapsed. Team short names use the provider's
  short/code value when present, otherwise the standardized team name.
- Team/league names are not identity keys. Persistence must resolve secondary providers through
  `fb_league_external_ids` and `fb_team_external_ids`.
- `FOOTBALL_DATA` remains the canonical source allowed to create/update core league/team records.
  A secondary provider with no explicit external-ID mapping is skipped and logged, rather than
  guessed into a canonical entity based on a similar name.

## Registering a secondary-provider team

Replace the IDs with actual values from the provider and MMO database:

```sql
INSERT INTO fb_team_external_ids (team_id, provider, external_id)
VALUES (<MMO_TEAM_ID>, 'API_FOOTBALL', '<API_FOOTBALL_TEAM_ID>');
```

For SofaScore use `'SOFA_SCORE'`; for Understat use `'UNDER_STATS'`. A team can have one external ID
per provider due to the unique constraints on `(team_id, provider)` and `(provider, external_id)`.

This strict mapping prevents name aliases from silently attaching statistics or fixtures to the wrong
MMO team. Display-name alias additions belong in the shared standardizer; identity corrections belong
in the external-ID mapping tables.
