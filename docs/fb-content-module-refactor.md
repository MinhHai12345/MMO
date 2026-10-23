# FB module architecture and persistence ownership

## Top-level business modules

```text
com.mmo.module.fb
├── admin/          # Operational/admin endpoints
├── ingest/         # Provider adapters, synchronization, external-ID mapping, data quality
├── league/         # League, season, standings and their external IDs
├── team/           # Team, team form and external IDs
├── player/         # Player, player match statistics and external IDs
├── match/          # Fixtures, odds, H2H and match-specific source data
├── analytics/      # Predictions, insights, model evaluation, backtesting and calibration
├── content/        # Editorial drafts, versions, generation, review and content workflow
├── publish/        # Cross-social publication, scheduling, delivery, attempts and external post IDs
├── monetization/   # Subscribers, plans, affiliate tracking, transactions and subscription lifecycle
└── report/         # Cross-module read models and reporting (add when reporting use cases are implemented)
```

## Persistence ownership

- `league.entity/repository`: `League`, `Season`, their external IDs, and `LeagueStanding`.
- `team.entity/repository`: `Team`, `TeamExternalId`, and `TeamForm`.
- `player.entity/repository`: `Player`, `PlayerExternalId`, and `PlayerMatchStats`.
- `match.entity/repository`: `Match`, match external IDs, advanced stats, odds, odds history, H2H records, and bookmaker metadata.
- `analytics.entity/repository`: `MatchPrediction`, `MatchInsight`, `MatchNarratives`, and prediction performance.
- `content.entity/repository`: editorial templates and the existing draft/version/workflow persistence.
- `publish.entity/repository`: social posts, delivery queue, publication records, and publication attempts.
- `monetization.entity/repository`: subscribers, subscription plans, tip transactions, and affiliate tracking.
- `ingest.entity/repository`: crawler health and ingestion data-quality metrics.

The legacy top-level `domain` package has been removed. Entity table names, column names, join-column names, and `mappedBy` property names are preserved by the package refactor. When changing a relationship, update both sides together and verify the owning side, inverse side, repository query paths, and application imports.

## Job ownership

- `ingest.job`: provider synchronization, fixture status/results, lineups/odds updates, crawler health and cleanup.
- `analytics.job`: prediction generation, match insights, evaluation and walk-forward backtesting.
- `content.job`: content scheduling.
- Disabled no-op recap/dashboard scheduler stubs were removed instead of registering jobs that perform no work.
- `publish.job`: publication/notification delivery.
- `monetization.job`: subscription expiry lifecycle.

## Publishing boundaries

- `publish.controller`: HTTP transport for publication workflows.
- `publish.service` and `publish.service.impl`: application contracts and orchestration.
- `publish.domain`: platform identifiers, state transitions, payload validation.
- `publish.provider.<platform>`: platform-specific adapters. Telegram currently uses the shared `TelegramClient` configured by the application.
- Future Facebook, X, YouTube, TikTok and Instagram adapters belong under `publish.provider.<platform>`; do not put these under `content`.

## Dependency rules

1. `ingest` adapts provider data into domain-owned entities; it should not own league/team/player/match business entities.
2. `analytics` consumes football data and owns analysis outputs. Prediction generation should not live in ingestion jobs.
3. `content` owns editorial creation and review. It should not perform external social delivery.
4. `publish` owns delivery state, idempotency, attempts, retry policy, scheduling, and provider-specific payloads.
5. `monetization` owns subscriber and revenue lifecycles.
6. Cross-module calls should use application services/contracts, not direct access to another module's repository when a service boundary exists.
7. `report` should aggregate via read models/projections; it must not duplicate another module's JPA entity.
8. Keep table/column/join mappings stable during package-only refactors. Validate JPA metadata and application context in addition to compilation.
