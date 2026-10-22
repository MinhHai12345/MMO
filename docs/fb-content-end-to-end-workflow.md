# FB End-to-End Content Workflow

## Scope

This change strengthens the existing Analytics-to-Content workflow without introducing automatic publishing.

1. Discover candidate matches using analytics discovery.
2. Deduplicate candidates by match ID before generation.
3. Generate the requested format and run the deterministic quality gate.
4. Record per-match generation failures without aborting the remaining candidates.
5. Persist the run, drafts and initial versions transactionally.
6. Route editorial review through the lifecycle service.

## Request idempotency

POST /api/fb/content/workflows/generate accepts an optional Idempotency-Key header (maximum 128 characters).

- Repeating a request with the same key and same filters returns the existing workflow run instead of generating duplicate drafts.
- Reusing a key with different filters returns HTTP 409.
- Omitting the header preserves existing behavior and starts a new run.
- A database unique constraint prevents persisted runs from sharing a non-null key.

Clients should generate a fresh key for each intentional run and reuse it only when retrying that same request.

## Known limits

- The endpoint remains synchronous; this does not introduce a background queue or persisted per-step checkpoints.
- Idempotency avoids repeat work for already persisted requests. A process crash before the run transaction commits may require a client retry.
- Publishing remains outside this workflow. Only editorially approved content should be sent to a future publisher.
- Data readiness is a quality signal, not a match prediction or factual guarantee.
- Run mvn clean compile and mvn test before merge; tests have not been executed in this environment.
