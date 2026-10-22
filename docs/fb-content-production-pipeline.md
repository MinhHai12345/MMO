# FB content production pipeline

## Request flow

1. Analytics assembles match/team/model insights.
2. The content generator creates a draft for `PRE_MATCH`, `POST_MATCH`, `LONG_VIDEO` or `SHORTS`.
3. The deterministic quality gate blocks missing/placeholder evidence and prohibited betting/guarantee language.
4. The social package provides platform copy and a script.
5. The production-plan endpoint adds claim review records, visual/shot cues and caption timing cues.
6. An editor verifies factual claims and rights, approves the package, then a platform adapter may publish it.

## Endpoints

- `GET /api/fb/content/workflows/matches/{matchId}/social-package?format=PRE_MATCH`
- `GET /api/fb/content/workflows/matches/{matchId}/production-plan?format=SHORTS`

Supported formats: `PRE_MATCH`, `POST_MATCH`, `LONG_VIDEO`, `SHORTS`.

## Claim provenance

`ContentClaimProvenance` records the claim, type, verification state, source provider/resource/external ID/field, observation timestamp and optional source URL. A claim marked `VERIFIED` must include at least one source reference. Current generated text is deliberately marked `UNVERIFIED` unless a trusted verifier attaches actual source metadata; an evidence string is not itself proof.

## Publishing and scheduling

`ContentPublisher` is an adapter port for official platform APIs. `PublicationStateMachine` defines legal lifecycle transitions, including review/approval, scheduling, retries and terminal published state. These are contracts only: durable queueing, schedule persistence, platform credentials, OAuth permissions, delivery receipts and provider-specific adapters must be configured/implemented before live automatic publishing is enabled. The API does not claim to have published anything.

## Media and rights

Production plans provide suggestions for original graphics, tactical boards, licensed footage and captions. They do not download footage, create finished video files, generate audio, or grant usage rights. Editors must confirm rights and review the generated subtitles against the final recording.

## Validation

Run `mvn clean compile` and `mvn test` in the project environment. Integration tests against real platform APIs require credentials and should use provider sandboxes where available.

## Performance feedback
`ContentPerformanceMetrics` normalizes impressions, views, clicks, reactions, shares, average watch duration and video length. It calculates CTR, engagement rate and watch-completion percentage while returning null when a denominator is unavailable. Actual metric ingestion and historical storage require official platform insights APIs, account permissions, and a persisted metrics store; those integrations are not enabled by this contract alone.
