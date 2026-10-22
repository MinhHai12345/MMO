# FB Content Module Architecture

## Responsibilities

- `content.controller`: HTTP transport only. Package-generation routes and workflow/lifecycle routes are separate controllers.
- `content.service`: application contracts for social packages and workflow orchestration/lifecycle.
- `content.service.impl`: workflow orchestration, editorial quality checks, persistence coordination and package composition.
- `content.persistence`: JPA entities and repositories only.
- `content.production`: production-plan DTOs and the production-plan use case.
- `content.provenance`: claim/source verification metadata.
- `content.video`: video content mapping and video generation boundary.

## Quality and review invariants

1. A draft requires a positive match ID, a format, title, opening hook, outline and traceable evidence references.
2. Placeholder evidence and betting/guaranteed-outcome language block a draft. The quality gate checks the full draft text, including evidence and caveats.
3. A quality gate is not a factual verifier. Claims remain unverified until source-level provenance is attached and an editor checks the result.
4. Editorial review follows explicit transitions: `PENDING_REVIEW -> APPROVED | CHANGES_REQUESTED | REJECTED`; `CHANGES_REQUESTED -> PENDING_REVIEW | APPROVED | REJECTED`; approved/rejected drafts can only be reopened through `CHANGES_REQUESTED`.
5. A blocked draft cannot be approved.
6. Workflow generation occurs before the persistence transaction, avoiding a long database transaction around analytics and content generation. Persisting the run, drafts and initial versions is transactional in the persistence service.

## API compatibility

Existing workflow routes remain under `/api/fb/content/workflows`. The package and production-plan routes are implemented by `ContentPackageController`; workflow run and draft lifecycle routes remain in `ContentWorkflowController`.

## Video generation limitation

The current `VideoGenerator` contract does not yet have a configured FFmpeg rendering pipeline. `generateVideo` now fails explicitly instead of returning a fake `File("script")` that looks like a successful render. Script generation produces neutral editorial copy and does not use betting hooks. A real implementation must create and validate a real output file before reporting success.

## Validation

Run `mvn clean compile` and `mvn test` before merging. This refactor was applied through the GitHub repository connector; the Maven build has not been executed in this environment.
