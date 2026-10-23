# FB content provenance and publishing persistence

## Canonical claim provenance

`content.provenance.ContentClaimProvenance` is the single provenance model for editorial claims. It records the claim type, verification state, match, one or more source references, source observation timestamps, FB capture time, methodology, and limitations.

- `observedAt` describes when the source observation was made (when the provider exposes it).
- `capturedAt` describes when FB captured the evidence.
- Neither timestamp implies the claim is verified.
- `isTraceable()` validates the minimum metadata; it is not a factuality classifier.
- `VERIFIED` requires at least one source reference and must only be assigned by a trusted verification step or an editor.

The former analytics-owned `ContentClaimProvenance` was removed so analytics does not maintain a parallel editorial provenance type.

## Publication persistence source of truth

| Concern | Canonical persistence | Invariant |
| --- | --- | --- |
| Publication request and lifecycle | `ContentPublicationEntity` | One row per platform/idempotency key; owns schedule, approval, state and external post ID |
| Delivery attempt history | `ContentPublicationAttemptEntity` | Append/update one row per attempt number; retain execution mode, outcome and timestamps |
| Current attempt summary | Fields on `ContentPublicationEntity` | Denormalized operational snapshot; must agree with the latest attempt |
| Legacy social post queue/log | Removed from entity model | Was not connected to the current publication workflow and had no active worker/repository consumers beyond the legacy repositories and relationship mapping test |

The old `ContentQueue`, `NotificationQueue`, `SocialPost`, `SocialPostLog`, their queue/social-post repositories, and their queue-only enums were removed. They duplicated lifecycle/status/retry concepts without an active worker path. New publishing work must use the publication aggregate and attempt history rather than reintroducing a second queue model.

## Scheduling and retry boundary

The persistence model now has one lifecycle source of truth, but a production scheduler/worker still needs to claim due `SCHEDULED` rows safely, avoid concurrent delivery, and use bounded retry/backoff. Do not treat the existence of `scheduledAt` as proof that scheduled delivery is implemented. When that worker is added, it should use row locking/claim semantics and record every delivery attempt in `ContentPublicationAttemptEntity`.

## Schema note

This refactor removes legacy JPA entities and therefore removes their tables when the schema is recreated. For an existing production database, ship a reviewed migration only after data retention/export requirements are checked. The project is still in active schema implementation; tests and CI must validate entity discovery and relationships.
