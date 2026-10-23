# Content publishing workflow — persisted dry-run stage

## Scope

This stage adds a durable publication request and approval boundary around existing content drafts. It intentionally does **not** publish to Telegram, Facebook, X, YouTube, Instagram or TikTok. The only registered adapter is `DryRunContentPublisher`; it performs no network calls and never returns an external post ID.

Scheduling timestamps are persisted for review, but there is no scheduler or live delivery worker in this stage.

## Workflow

1. Generate and persist a content draft through the existing workflow.
2. Complete editorial review; the source draft must have `editorialStatus=APPROVED` and must not be blocked.
3. Create a platform-specific publication request with an `Idempotency-Key`. The request enters `NEEDS_REVIEW`.
4. Review and approve the exact title/body/media reference. A future `scheduledAt` moves the request to `SCHEDULED`; otherwise it moves to `APPROVED`.
5. Call the dry-run endpoint. The payload is validated and a persisted attempt record is written. The publication remains `APPROVED` or `SCHEDULED`; it never becomes `PUBLISHED` because no platform API was called.
6. Inspect the publication detail and attempt history before adding a real provider adapter.

## Endpoints

### Create a publication request

```http
POST /api/fb/content/publications
Idempotency-Key: match-123-telegram-v1
Content-Type: application/json
```

```json
{
  "draftId": 123,
  "platform": "TELEGRAM",
  "title": "Team A vs Team B — match preview",
  "body": "A data-led preview with limitations and source caveats.",
  "mediaReference": null,
  "scheduledAt": "2030-01-01T12:00:00Z",
  "requestedBy": "editor"
}
```

The same platform/key and same request returns the existing request. Reusing the key with different content returns HTTP 409. The database unique constraint prevents duplicate publication-request rows in a concurrent race.

### List and inspect requests

- `GET /api/fb/content/publications?state=NEEDS_REVIEW&limit=20`
- `GET /api/fb/content/publications/{publicationId}`

### Approve the exact publication payload

```http
POST /api/fb/content/publications/{publicationId}/approve
Content-Type: application/json
```

```json
{
  "approvedBy": "editor",
  "notes": "Reviewed claims, caveats and media rights."
}
```

Approval is rejected unless the source draft is still editorially approved and the publication is in `NEEDS_REVIEW`.

### Dry-run the approved payload

```http
POST /api/fb/content/publications/{publicationId}/dry-run
Content-Type: application/json
```

```json
{
  "requestedBy": "operator"
}
```

The response has `lastAttemptStatus=DRY_RUN`, a null `externalPostId`, and an attempt-history record stating that no external API was called.

## State and safety rules

- Legal transitions are validated by `PublicationStateMachine`.
- A draft must already be editorially approved; blocked drafts cannot enter publishing.
- A publication request requires its own approval after title/body/media have been fixed.
- The idempotency key is unique per platform.
- Approval and attempt records are persisted.
- No state is marked `PUBLISHED` during dry-run.
- Platform names are destinations only; selecting a platform does not mean credentials or an official adapter exist.
- The current `requestedBy` / `approvedBy` values are audit labels supplied by the caller. They are **not authenticated identities**. Secure these endpoints with application authentication/authorization before exposing them publicly.

## Before live publishing

Implement a separate official API adapter per platform, using least-privilege credentials and provider-specific permission checks. Add timeouts, rate limits, retry classification, delivery receipts, duplicate-send protection, cancellation semantics, and provider sandbox/integration tests. Only transition to `PUBLISHED` after the platform confirms success and returns a real external post ID. Do not convert an accepted HTTP request into a success state without a delivery receipt.
