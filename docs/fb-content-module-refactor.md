# FB module architecture refactor

## Module responsibilities

- `content` owns editorial content generation, drafts, versions, review, and content workflow.
- `publish` is a peer module to `content`. It owns publication requests, platform-specific delivery, publication state, idempotency, attempts, retries, and external post identifiers.
- `content.production` remains limited to production planning and performance metrics; it does not own social delivery.
- `analytics` owns football analysis and prediction; content consumes its results through application-level contracts.
- `ingest` owns provider integrations and synchronization workflows.

## Publishing package boundaries

- `publish.controller`: HTTP endpoints for publication workflows.
- `publish.service`: publication application contracts.
- `publish.service.impl`: publication orchestration and dry-run implementation.
- `publish.domain`: platform identifiers, state transitions, and payload validation.
- `publish.entity` / `publish.repository`: publication persistence.
- `publish.provider.telegram`: Telegram delivery adapter and live publishing orchestration.
- Future providers (Facebook, X, YouTube, TikTok, Instagram) should be added under `publish.provider.<platform>` when implemented.

## Persistence ownership

- Draft and editorial workflow entities/repositories remain in `content.entity` and `content.repository`.
- Publication and publication-attempt entities/repositories belong to `publish.entity` and `publish.repository`.
- Avoid a global `domain` package that owns unrelated entities from content, publishing, football data, and monetization. Each domain module should own its entities and repositories.

## Dependency rules

- `content` must not depend on `publish`.
- `publish` may consume approved content through an explicit content-facing application contract; it should not own editorial workflow.
- Provider adapters must not be called directly by controllers.
- Keep idempotency, approval, publication state, and attempt tracking in the publish module.
