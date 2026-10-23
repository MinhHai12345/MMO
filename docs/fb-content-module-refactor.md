# FB Content Module Architecture

## Responsibilities and package boundaries

- `content.controller`: HTTP transport only; controllers delegate to application services.
- `content.service` / `content.service.impl`: content workflow, editorial lifecycle, package composition and orchestration.
- `content.generator`: creates editorial content from analytics inputs; it does not publish to external platforms.
- `content.entity`: JPA entities for drafts, workflow runs/items/versions, publications and publication attempts.
- `content.repository`: Spring Data repositories for those entities. Entity and repository types are not placed under a generic `persistence` package.
- `content.publish`: social publishing contracts, publication state machine, platform definitions and payload validation.
- `content.publish.impl`: publication workflow implementation and dry-run publisher.
- `content.publish.telegram`: Telegram-specific adapter and live publishing orchestration, using the shared application `TelegramClient`.
- `content.production`: production planning and performance metrics only; social publishing does not belong here.
- `content.provenance`: claim/source verification metadata.
- `content.video`: video content mapping and video generation boundary.

## Quality and review invariants

1. A draft requires a positive match ID, a format, title, opening hook, outline and traceable evidence references.
2. Placeholder evidence and betting/guaranteed-outcome language block a draft. The quality gate checks the full draft text, including evidence and caveats.
3. A quality gate is not a factual verifier. Claims remain unverified until source-level provenance is attached and an editor checks the result.
4. Editorial review follows explicit transitions: `PENDING_REVIEW -> APPROVED | CHANGES_REQUESTED | REJECTED`; `CHANGES_REQUESTED -> PENDING_REVIEW | APPROVED | REJECTED`; approved/rejected drafts can only be reopened through `CHANGES_REQUESTED`.
5. A blocked draft cannot be approved.
6. Workflow generation occurs before the persistence transaction, avoiding a long database transaction around analytics and content generation. Persisting the run, drafts and initial versions is transactional in the workflow persistence service.

## API compatibility

Workflow routes remain under `/api/fb/content/workflows`; publication management remains under `/api/fb/content/publications`. Package generation and production-plan routes remain in `ContentPackageController`; workflow run and draft lifecycle routes remain in `ContentWorkflowController`.

## Video generation limitation

The current `VideoGenerator` contract does not yet have a configured FFmpeg rendering pipeline. `generateVideo` fails explicitly instead of returning a fake `File("script")` that looks like a successful render. Script generation produces neutral editorial copy and does not use betting hooks. A real implementation must create and validate a real output file before reporting success.

## Validation

Run `mvn -B -ntp clean verify` before merging.
