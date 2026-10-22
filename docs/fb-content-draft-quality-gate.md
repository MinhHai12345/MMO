# Content Draft Generator and Editorial Quality Gate

## Endpoint

`GET /api/fb/analytics/matches/{matchId}/content-draft?format=PRE_MATCH`

Supported formats: `PRE_MATCH`, `POST_MATCH`, `LONG_VIDEO`, `SHORTS`.

The generator creates a structured editorial draft from the existing Social Insight payload. It does not call an LLM, fabricate missing statistics, or publish automatically.

## Response

The response includes title, opening hook, outline, Shorts hooks, available evidence, factual caveats, and a quality review.

Quality decisions:
- `BLOCKED`: required structure/team identity is missing, prohibited betting language appears, or a post-match draft is requested without post-match analysis.
- `REVIEW_REQUIRED`: draft structure exists, but data warnings or missing evidence require an editor to verify the claims.
- `READY_FOR_EDITOR_REVIEW`: no current blockers or warnings were detected. This is not automatic approval to publish.

## Guardrails

- Uses the existing analytics payload as its evidence boundary.
- Carries data warnings and factual caveats into the draft.
- Does not treat model probabilities as certainty.
- Avoids player-specific claims when player statistics are absent.
- Keeps betting/gambling language out of the generated football editorial text.
- Post-match generation requires a finished-match analysis.

The current implementation is a deterministic skeleton. A later language-generation component can improve prose while preserving the same quality gate and evidence contract.
