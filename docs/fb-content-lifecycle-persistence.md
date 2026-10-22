# Content Lifecycle & Persistence

## Durable workflow records

- `fb_content_workflow_runs` stores workflow filters, lifecycle status, timing, counts, and warnings.
- `fb_content_drafts` stores each generated draft, evidence, caveats, quality review, and editorial status.
- `fb_content_draft_versions` stores immutable snapshots when a draft is generated or its editorial status changes.

The entities are managed by the project's existing Hibernate schema update configuration. JSON payloads are stored as LONGTEXT for MySQL compatibility; they are serialized through Jackson.

## API

- `POST /api/fb/content/workflows/generate?limit=20&format=PRE_MATCH`
- `GET /api/fb/content/workflows/runs?limit=20`
- `GET /api/fb/content/workflows/drafts?editorialStatus=PENDING_REVIEW&limit=20`
- `GET /api/fb/content/workflows/drafts/{draftId}`
- `GET /api/fb/content/workflows/drafts/{draftId}/versions`
- `PATCH /api/fb/content/workflows/drafts/{draftId}/review`

Review request example:

```json
{
  "status": "CHANGES_REQUESTED",
  "notes": "Verify the evidence and narrow the tactical claim.",
  "changedBy": "editor"
}
```

Allowed editorial states: `PENDING_REVIEW`, `CHANGES_REQUESTED`, `APPROVED`, `REJECTED`.

Approval is editorial approval only. It does not publish or distribute content. Draft generation failures are reflected in run counts/warnings; only successfully generated draft payloads receive draft records. Each generation run creates a new run and draft records; it does not overwrite prior runs. A blocked draft cannot be approved until it is regenerated after fixing the blockers.

## Operational notes

- Provider calls and draft generation run before the persistence transaction; only the completed report is written in a short transaction.
- This first version is synchronous and is not a durable job queue.
- Data readiness is not a prediction of match outcomes.
